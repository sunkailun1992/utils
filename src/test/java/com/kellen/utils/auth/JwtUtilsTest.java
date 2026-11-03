package com.kellen.utils.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    @BeforeEach
    void isolatedSigningKey() {
        configureKey(new byte[32]);
    }

    @Test
    void missingMalformedAndWeakKeysFailClosed() {
        for (String key : new String[]{"", "not-base64", Base64.getEncoder().encodeToString(new byte[16])}) {
            new JwtKeyConfig(new MockEnvironment().withProperty("security.jwt.secret", key));
            assertThatThrownBy(() -> JwtUtils.createJwt("jti", "subject", Map.of()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void explicitIssuerIsPreservedAndOtherEnvironmentKeysCannotReadTheToken() {
        String token = JwtUtils.createJwt("jti", "subject", Map.of(), 60_000, "https://issuer.example.test");
        assertThat(JwtUtils.parseJwt(token).getIssuer()).isEqualTo("https://issuer.example.test");
        byte[] otherKey = new byte[32];
        java.util.Arrays.fill(otherKey, (byte) 1);
        configureKey(otherKey);
        assertThatThrownBy(() -> JwtUtils.parseJwt(token)).isInstanceOf(io.jsonwebtoken.SignatureException.class);
    }

    @Test
    void defaultIssuerComesFromCurrentEnvironment() {
        assertThat(JwtUtils.parseJwt(JwtUtils.createJwt("jti", "subject", Map.of())).getIssuer())
                .isEqualTo("synthetic-test-issuer");
    }

    private static void configureKey(byte[] key) {
        new JwtKeyConfig(new MockEnvironment().withProperty("security.jwt.secret", Base64.getEncoder().encodeToString(key))
                .withProperty("security.jwt.issuer", "synthetic-test-issuer"));
    }

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
