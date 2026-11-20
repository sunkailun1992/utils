package com.kellen.security.oauth2;

import com.kellen.datapermission.DataPermissionContextHolder;
import com.kellen.security.UserContextHolder;
import com.kellen.utils.context.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把认证结果里的业务上下文写进 {@link UserContextHolder}（数据权限插件也从这里读）与 {@link TenantContextHolder}，
 * 请求结束清理用户、租户与数据权限忽略标记，避免线程复用串号。排在 Bearer 令牌认证过滤器之后。
 *
 * @author 孙凯伦
 */
public class FleetSecurityContextFilter extends OncePerRequestFilter {

    /**
     * 创建过滤器，由资源服务器过滤链加在 Bearer 令牌认证之后。
     */
    public FleetSecurityContextFilter() {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof FleetAuthenticationToken token && token.isActive()) {
                UserContextHolder.set(token.getUser());
                TenantContextHolder.setTenantId(token.getUser().getTenantId());
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            TenantContextHolder.clear();
            DataPermissionContextHolder.clear();
        }
    }
}
