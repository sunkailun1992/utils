package com.kellen.security;

import com.kellen.security.config.SecurityAuthProperties;
import com.kellen.utils.auth.JwtUtils;
import com.kellen.utils.context.TenantContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Spring Security认证上下文过滤器。
 *
 * <p>当前过滤器只从 Authorization Bearer JWT 或网关透传请求头解析用户信息，
 * 不再读取历史 token 请求头，也不再依赖 Redis 中的 token 用户对象。</p>
 *
 * @author 孙凯伦
 */
public class SecurityAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Bearer认证请求头前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 认证配置属性。
     */
    private final SecurityAuthProperties securityAuthProperties;

    /**
     * 构造认证上下文过滤器。
     *
     * @param securityAuthProperties 认证配置属性
     */
    public SecurityAuthenticationFilter(SecurityAuthProperties securityAuthProperties) {
        this.securityAuthProperties = securityAuthProperties; // 保存配置，后续按开关决定是否解析 JWT 或请求头。
    }

    /**
     * 判断当前请求是否为公开白名单。
     *
     * @param request 当前HTTP请求
     * @return true 表示跳过认证上下文解析
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return securityAuthProperties.getPermitUrls().stream()
                .anyMatch(url -> PathPatternRequestMatcher.pathPattern(url).matches(request)); // 在过滤器入口直接跳过公开接口，保证登录和文档等白名单不被认证逻辑影响。
    }

    /**
     * 解析当前请求用户并写入 Spring Security 上下文。
     *
     * @param request     当前HTTP请求
     * @param response    当前HTTP响应
     * @param filterChain 后续过滤器链
     * @throws ServletException Servlet过滤异常
     * @throws IOException      IO异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            if (securityAuthProperties.isEnabled()) { // 认证总开关关闭时不解析用户，兼容老服务升级。
                SecurityUser user = resolveUser(request); // 按 JWT 优先、请求头兜底的顺序解析当前用户。
                if (user != null) {
                    UserContextHolder.set(user); // 写入业务代码可直接读取的用户上下文。
                    if (StringUtils.isNotBlank(user.getTenantId())) {
                        TenantContextHolder.setTenantId(user.getTenantId()); // 有租户时同步写入租户上下文，供 MyBatis-Plus 租户插件使用。
                    }
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList()); // 将权限码转换为 Spring Security 权限对象。
                    SecurityContextHolder.getContext().setAuthentication(authentication); // 写入 Spring Security 上下文，支持 @PreAuthorize 等方法级鉴权。
                }
            }
            filterChain.doFilter(request, response); // 放行到后续过滤器和业务接口。
        } finally {
            UserContextHolder.clear(); // 请求结束清理用户上下文，避免线程复用导致串用户。
            TenantContextHolder.clear(); // 请求结束清理租户上下文，避免线程复用导致串租户。
            SecurityContextHolder.clearContext(); // 请求结束清理 Spring Security 上下文。
        }
    }

    /**
     * 解析请求用户。
     *
     * @param request 当前HTTP请求
     * @return 当前认证用户，无法解析时返回 null
     */
    private SecurityUser resolveUser(HttpServletRequest request) {
        SecurityUser jwtUser = resolveJwtUser(request); // 优先解析服务端签发的 Bearer JWT。
        if (jwtUser != null) {
            return jwtUser; // JWT 有效时直接使用 JWT 用户信息。
        }
        return resolveHeaderUser(request); // JWT 不存在或无效时尝试读取网关透传用户头。
    }

    /**
     * 从 Authorization Bearer JWT 中解析用户。
     *
     * @param request 当前HTTP请求
     * @return 当前认证用户，无法解析时返回 null
     */
    private SecurityUser resolveJwtUser(HttpServletRequest request) {
        if (!securityAuthProperties.isJwtEnabled()) {
            return null; // JWT 开关关闭时不读取 Authorization。
        }
        String authorization = request.getHeader("Authorization"); // 读取标准认证请求头。
        if (StringUtils.isBlank(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null; // 没有 Bearer 前缀时不按 JWT 处理。
        }
        try {
            Claims claims = JwtUtils.parseJwt(authorization.substring(BEARER_PREFIX.length())); // 去掉 Bearer 前缀并校验 JWT 签名。
            String userId = firstNotBlank(claims.get("userId", String.class), claims.getSubject()); // 优先使用 userId 声明，缺失时用 subject。
            String username = firstNotBlank(claims.get("username", String.class), claims.get("userName", String.class)); // 兼容 username 与 userName 两种声明。
            String tenantId = claims.get("tenantId", String.class); // 读取租户ID声明。
            String deptId = claims.get("deptId", String.class); // 读取部门ID声明。
            String dataScope = claims.get("dataScope", String.class); // 读取数据权限范围声明。
            List<String> dataScopeDeptIds = parseValues(claims.get("dataScopeDeptIds")); // 读取数据权限部门ID集合。
            List<String> authorities = parseAuthorities(claims.get("permissions"), claims.get("roles")); // 读取权限码和角色码。
            return buildUser(userId, username, tenantId, deptId, dataScope, dataScopeDeptIds, authorities); // 构造统一安全用户对象。
        } catch (Exception ignored) {
            return null; // JWT 无效或过期时不抛出底层异常，由 Spring Security 继续按未认证处理。
        }
    }

    /**
     * 从网关透传请求头中解析用户。
     *
     * @param request 当前HTTP请求
     * @return 当前认证用户，无法解析时返回 null
     */
    private SecurityUser resolveHeaderUser(HttpServletRequest request) {
        if (!securityAuthProperties.isHeaderEnabled()) {
            return null; // 请求头透传开关关闭时不读取网关用户头。
        }
        String userId = request.getHeader(securityAuthProperties.getUserIdHeader()); // 读取用户ID头。
        String username = request.getHeader(securityAuthProperties.getUsernameHeader()); // 读取用户名头。
        String tenantId = request.getHeader(securityAuthProperties.getTenantIdHeader()); // 读取租户ID头。
        String deptId = request.getHeader(securityAuthProperties.getDeptIdHeader()); // 读取部门ID头。
        String dataScope = request.getHeader(securityAuthProperties.getDataScopeHeader()); // 读取数据权限范围头。
        List<String> dataScopeDeptIds = parseValues(request.getHeader(securityAuthProperties.getDataScopeDeptIdsHeader())); // 读取数据权限部门ID集合头。
        List<String> authorities = parseAuthorities(
                request.getHeader(securityAuthProperties.getAuthoritiesHeader()),
                request.getHeader(securityAuthProperties.getRolesHeader())); // 解析权限头和角色头。
        return buildUser(userId, username, tenantId, deptId, dataScope, dataScopeDeptIds, authorities); // 构造统一安全用户对象。
    }

    /**
     * 构造安全用户对象。
     *
     * @param userId      用户ID
     * @param username    用户名
     * @param tenantId    租户ID
     * @param deptId           部门ID
     * @param dataScope        数据权限范围
     * @param dataScopeDeptIds 数据权限部门ID集合
     * @param authorities      权限集合
     * @return 安全用户对象，缺少用户标识时返回 null
     */
    private SecurityUser buildUser(String userId, String username, String tenantId, String deptId, String dataScope, List<String> dataScopeDeptIds, List<String> authorities) {
        if (StringUtils.isBlank(userId) && StringUtils.isBlank(username)) {
            return null; // 用户ID和用户名都为空时视为未认证请求。
        }
        return new SecurityUser(userId, username, tenantId, deptId, dataScope, dataScopeDeptIds, authorities); // 返回不可变语义的安全用户快照。
    }

    /**
     * 合并权限码和角色码。
     *
     * @param permissions 权限码原始值
     * @param roles       角色码原始值
     * @return Spring Security 权限字符串集合
     */
    private List<String> parseAuthorities(Object permissions, Object roles) {
        List<String> authorities = new ArrayList<>(); // 创建权限集合。
        addAuthorities(authorities, permissions, false); // 权限码按原值加入。
        addAuthorities(authorities, roles, true); // 角色码按 ROLE_ 前缀规则加入。
        return authorities; // 返回合并后的权限集合。
    }

    /**
     * 解析逗号分隔的原始值。
     *
     * @param raw 原始值
     * @return 字符串集合
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private List<String> parseValues(Object raw) {
        List<String> values = new ArrayList<>(); // 创建结果集合。
        addAuthorities(values, raw, false); // 复用逗号和集合解析逻辑，但不追加角色前缀。
        return values; // 返回解析后的字符串集合。
    }

    /**
     * 追加权限或角色原始值。
     *
     * @param authorities 目标权限集合
     * @param raw         原始权限值
     * @param role        是否按角色处理
     */
    private void addAuthorities(List<String> authorities, Object raw, boolean role) {
        if (raw == null) {
            return; // 原始值为空时无需追加。
        }
        if (raw instanceof Collection<?> collection) {
            collection.stream().filter(Objects::nonNull).forEach(value -> addAuthority(authorities, String.valueOf(value), role)); // 集合类型逐项追加。
            return; // 集合已处理完毕。
        }
        String.valueOf(raw).replace("[", "").replace("]", "").lines()
                .flatMap(line -> List.of(line.split(",")).stream()) // 字符串类型兼容逗号分隔和多行文本。
                .forEach(value -> addAuthority(authorities, value, role)); // 逐项追加到权限集合。
    }

    /**
     * 追加单个权限或角色。
     *
     * @param authorities 目标权限集合
     * @param value       原始权限字符串
     * @param role        是否按角色处理
     */
    private void addAuthority(List<String> authorities, String value, boolean role) {
        String authority = StringUtils.trimToNull(value); // 去掉空白字符，空串直接转为 null。
        if (authority == null) {
            return; // 空权限不加入集合。
        }
        if (role && !authority.startsWith("ROLE_")) {
            authority = "ROLE_" + authority; // Spring Security 角色统一使用 ROLE_ 前缀。
        }
        authorities.add(authority); // 加入最终权限集合。
    }

    /**
     * 返回第一个非空字符串。
     *
     * @param first  第一个候选值
     * @param second 第二个候选值
     * @return 第一个非空字符串
     */
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second; // first 有值时优先使用 first。
    }
}
