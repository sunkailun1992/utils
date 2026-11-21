package com.kellen.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuedAtValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 访问令牌签发时间（{@code iat}）的校验：必须有（RFC 9068 §2.2），且不能晚于当前时间超过 60 秒的时钟偏差。
 *
 * <p>Spring Security 的 {@link JwtTimestampValidator} 只看 {@code exp} 与 {@code nbf}，Keycloak 不写 {@code nbf}，
 * 一张 iat 在未来的令牌（签发方时钟错乱或伪造）能一直用到 iat 加有效期。{@link JwtIssuedAtValidator} 是给 DPoP 证明用的双向窗口
 * （签发早于 60 秒也拒绝），不适用于 5 分钟寿命的访问令牌。这里只拦未来方向，与 PyJWT（RAG 引擎所用）、auth0 java-jwt 的默认行为一致；
 * 签发得早不归这里管，有效期由 {@code exp} 校验。</p>
 *
 * @author 孙凯伦
 */
public final class FleetIssuedAtValidator implements OAuth2TokenValidator<Jwt> {

    /**
     * 允许的时钟偏差，与 {@link JwtTimestampValidator} 的缺省值相同。
     */
    static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    private final Clock clock;

    /**
     * 按系统时钟校验。
     */
    public FleetIssuedAtValidator() {
        this(Clock.systemUTC());
    }

    /**
     * 按指定时钟校验（测试用）。
     *
     * @param clock 时钟
     */
    FleetIssuedAtValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        if (issuedAt == null) {
            return invalid("iat claim is required.");
        }
        if (issuedAt.isAfter(Instant.now(clock).plus(CLOCK_SKEW))) {
            return invalid("iat claim is in the future.");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult invalid(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description,
                "https://tools.ietf.org/html/rfc6750#section-3.1"));
    }
}
