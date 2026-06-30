package com.kellen.utils.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    @Test
    void createJwtShouldExpireAfterOneDay() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", "user-1");
        String token = JwtUtils.createJwt("test-jti", "user-1", claims);

        Claims parsedClaims = JwtUtils.parseJwt(token);

        assertThat(parsedClaims.getExpiration().getTime() - parsedClaims.getIssuedAt().getTime())
                .isEqualTo(24 * 60 * 60 * 1000L);
    }

    @Test
    void createJwtShouldUseCustomExpiration() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", "user-1");
        String token = JwtUtils.createJwt("test-jti", "user-1", claims, 30_000L);

        Claims parsedClaims = JwtUtils.parseJwt(token);

        assertThat(parsedClaims.getExpiration().getTime() - parsedClaims.getIssuedAt().getTime())
                .isEqualTo(30_000L);
    }
}
