package com.kellen.security;

import com.kellen.bean.SecurityAuthProperties;
import com.kellen.utils.JwtUtil;
import com.kellen.utils.TenantContextHolder;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class SecurityAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SecurityAuthProperties securityAuthProperties;

    public SecurityAuthenticationFilter(SecurityAuthProperties securityAuthProperties) {
        this.securityAuthProperties = securityAuthProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            if (securityAuthProperties.isEnabled()) {
                SecurityUser user = resolveUser(request);
                if (user != null) {
                    UserContextHolder.set(user);
                    if (StringUtils.isNotBlank(user.getTenantId())) {
                        TenantContextHolder.setTenantId(user.getTenantId());
                    }
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private SecurityUser resolveUser(HttpServletRequest request) {
        SecurityUser jwtUser = resolveJwtUser(request);
        if (jwtUser != null) {
            return jwtUser;
        }
        return resolveHeaderUser(request);
    }

    private SecurityUser resolveJwtUser(HttpServletRequest request) {
        if (!securityAuthProperties.isJwtEnabled()) {
            return null;
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.isBlank(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            Claims claims = JwtUtil.parseJwt(authorization.substring(BEARER_PREFIX.length()));
            String userId = firstNotBlank(claims.get("userId", String.class), claims.getSubject());
            String username = firstNotBlank(claims.get("username", String.class), claims.get("userName", String.class));
            String tenantId = claims.get("tenantId", String.class);
            List<String> authorities = parseAuthorities(claims.get("permissions"), claims.get("roles"));
            return buildUser(userId, username, tenantId, authorities);
        } catch (Exception ignored) {
            return null;
        }
    }

    private SecurityUser resolveHeaderUser(HttpServletRequest request) {
        if (!securityAuthProperties.isHeaderEnabled()) {
            return null;
        }
        String userId = request.getHeader(securityAuthProperties.getUserIdHeader());
        String username = request.getHeader(securityAuthProperties.getUsernameHeader());
        String tenantId = request.getHeader(securityAuthProperties.getTenantIdHeader());
        List<String> authorities = parseAuthorities(
                request.getHeader(securityAuthProperties.getAuthoritiesHeader()),
                request.getHeader(securityAuthProperties.getRolesHeader()));
        return buildUser(userId, username, tenantId, authorities);
    }

    private SecurityUser buildUser(String userId, String username, String tenantId, List<String> authorities) {
        if (StringUtils.isBlank(userId) && StringUtils.isBlank(username)) {
            return null;
        }
        return new SecurityUser(userId, username, tenantId, authorities);
    }

    private List<String> parseAuthorities(Object permissions, Object roles) {
        List<String> authorities = new ArrayList<>();
        addAuthorities(authorities, permissions, false);
        addAuthorities(authorities, roles, true);
        return authorities;
    }

    private void addAuthorities(List<String> authorities, Object raw, boolean role) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Collection<?> collection) {
            collection.stream().filter(Objects::nonNull).forEach(value -> addAuthority(authorities, String.valueOf(value), role));
            return;
        }
        String.valueOf(raw).replace("[", "").replace("]", "").lines()
                .flatMap(line -> List.of(line.split(",")).stream())
                .forEach(value -> addAuthority(authorities, value, role));
    }

    private void addAuthority(List<String> authorities, String value, boolean role) {
        String authority = StringUtils.trimToNull(value);
        if (authority == null) {
            return;
        }
        if (role && !authority.startsWith("ROLE_")) {
            authority = "ROLE_" + authority;
        }
        authorities.add(authority);
    }

    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
}
