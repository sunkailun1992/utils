package com.kellen.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访问令牌的 iat：必须有（RFC 9068 §2.2），且不能晚于当前时间 60 秒以上；签发得早不算错（有效期由 exp 管）。
 */
class FleetIssuedAtValidatorTest {

    private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

    private final FleetIssuedAtValidator validator = new FleetIssuedAtValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void issuedLongAgoIsNotTheValidatorsConcern() {
        assertThat(validator.validate(jwt(NOW.minusSeconds(3600))).hasErrors()).isFalse();
    }

    @Test
    void issuedUpToSixtySecondsAheadIsAccepted() {
        assertThat(validator.validate(jwt(NOW.plusSeconds(60))).hasErrors()).isFalse();
    }

    @Test
    void issuedMoreThanSixtySecondsAheadIsInvalidToken() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(NOW.plusSeconds(61)));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN));
    }

    @Test
    void missingIssuedAtIsInvalidToken() {
        Jwt withoutIat = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-1").build();
        OAuth2TokenValidatorResult result = validator.validate(withoutIat);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN));
    }

    private static Jwt jwt(Instant issuedAt) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-1").issuedAt(issuedAt).build();
    }
}
