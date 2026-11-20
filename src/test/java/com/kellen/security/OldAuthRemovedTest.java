package com.kellen.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 第5阶段起身份只由 Keycloak 签发：自签 HS256 JWT（jjwt）、旧认证过滤器与网关透传身份头的配置都已删除，不能再被加载。
 */
class OldAuthRemovedTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "io.jsonwebtoken.Jwts",
            "com.kellen.utils.auth.JwtUtils",
            "com.kellen.utils.auth.JwtKeyConfig",
            "com.kellen.security.SecurityAuthenticationFilter",
            "com.kellen.security.AuthTokenRedisKeys",
            "com.kellen.security.config.SecurityAuthConfig",
            "com.kellen.security.config.SecurityAuthProperties"
    })
    void oldAuthenticationClassesAreGone(String className) {
        assertThatThrownBy(() -> Class.forName(className, false, OldAuthRemovedTest.class.getClassLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
