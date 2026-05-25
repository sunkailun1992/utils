package com.kellen.bean;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private String userIdHeader = "X-User-Id";
    private String usernameHeader = "X-Username";
    private String tenantIdHeader = "X-Tenant-Id";
    private String authoritiesHeader = "X-Permissions";
    private String rolesHeader = "X-Roles";

    /**
     * 开启认证后仍放行的接口。
     */
    private List<String> permitUrls = new ArrayList<>(Arrays.asList(
            "/actuator/**",
            "/doc.html",
            "/webjars/**",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    ));
}
