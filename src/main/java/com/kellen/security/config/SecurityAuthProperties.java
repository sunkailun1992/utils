package com.kellen.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 统一认证配置属性。
 *
 * @author 孙凯伦
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security.auth")
public class SecurityAuthProperties {

    /**
     * 默认关闭，避免老服务升级 utils 后被 Spring Security 拦截。
     */
    private boolean enabled = false;

    /**
     * 是否允许网关透传用户信息。
     */
    private boolean headerEnabled = true;

    /**
     * 是否允许 Authorization: Bearer <jwt>。
     */
    private boolean jwtEnabled = true;

    /**
     * 网关透传用户ID请求头。
     */
    private String userIdHeader = "X-User-Id";

    /**
     * 网关透传用户名请求头。
     */
    private String usernameHeader = "X-Username";

    /**
     * 网关透传租户ID请求头。
     */
    private String tenantIdHeader = "X-Tenant-Id";

    /**
     * 网关透传部门ID请求头。
     */
    private String deptIdHeader = "X-Dept-Id";

    /**
     * 网关透传数据权限范围请求头。
     */
    private String dataScopeHeader = "X-Data-Scope";

    /**
     * 网关透传数据权限部门ID集合请求头。
     */
    private String dataScopeDeptIdsHeader = "X-Data-Scope-Dept-Ids";

    /**
     * 网关透传权限码请求头。
     */
    private String authoritiesHeader = "X-Permissions";

    /**
     * 网关透传角色码请求头。
     */
    private String rolesHeader = "X-Roles";

    /**
     * 开启认证后仍放行的接口。
     */
    private List<String> permitUrls = new ArrayList<>(Arrays.asList(
            "/actuator/**",
            "/webjars/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/auth/tenants",
            "/auth/sessions",
            "/auth/sessions/refresh"
    ));
}
