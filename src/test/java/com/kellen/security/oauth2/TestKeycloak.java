package com.kellen.security.oauth2;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 测试用的 Keycloak 替身：在本机随机端口提供 OIDC 发现文档与公钥集，并签发 RS256 访问令牌。
 *
 * <p>资源服务器走的是 Spring Boot 真实的按 issuer-uri 发现流程，只把对端换成本地进程。</p>
 */
final class TestKeycloak implements AutoCloseable {

    /**
     * 测试域路径，与正式环境的 github-dev 域同名。
     */
    static final String REALM_PATH = "/realms/github-dev";

    private final MockWebServer server = new MockWebServer();

    private final RSAKey signingKey;

    private final String issuer;

    private TestKeycloak() throws Exception {
        // 公钥不写 alg：资源服务器若按公钥集推断算法会放行 RS256/RS384/RS512，只有显式限定 RS256 才会拒绝 RS512。
        signingKey = new RSAKeyGenerator(2048).keyID("test-signing-key").keyUse(KeyUse.SIGNATURE).generate();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getUrl().encodedPath();
                if (path.equals(REALM_PATH + "/.well-known/openid-configuration")) {
                    return json(JSONObjectUtils.toJSONString(Map.of(
                            "issuer", issuer,
                            "jwks_uri", issuer + "/protocol/openid-connect/certs",
                            "token_endpoint", issuer + "/protocol/openid-connect/token",
                            "id_token_signing_alg_values_supported", java.util.List.of("RS256"))));
                }
                if (path.equals(REALM_PATH + "/protocol/openid-connect/certs")) {
                    return json(new JWKSet(signingKey.toPublicJWK()).toString());
                }
                return new MockResponse.Builder().code(404).build();
            }
        });
        server.start(InetAddress.getByName("127.0.0.1"), 0);
        issuer = server.url(REALM_PATH).newBuilder().host("127.0.0.1").build().toString();
    }

    /**
     * 启动替身。
     *
     * @return 已启动的替身
     */
    static TestKeycloak start() {
        try {
            return new TestKeycloak();
        } catch (Exception ex) {
            throw new IllegalStateException("测试 Keycloak 替身启动失败", ex);
        }
    }

    /**
     * 签发方地址，与令牌里的 iss 一致。
     *
     * @return 签发方地址
     */
    String issuer() {
        return issuer;
    }

    /**
     * 浏览器经网关登录后拿到的用户令牌：受众 github-api、azp=github-gateway、带 user_id。
     *
     * @param userId 业务用户 ID
     * @return 已签名的令牌
     */
    String userToken(String userId) {
        return token(claims -> claims.claim("user_id", userId));
    }

    /**
     * 客户端凭证方式拿到的机器令牌：没有 user_id，azp 为客户端 ID。
     *
     * @param clientId 客户端 ID
     * @return 已签名的令牌
     */
    String machineToken(String clientId) {
        return token(claims -> claims.claim("azp", clientId));
    }

    /**
     * 签发令牌：先填默认声明（5 分钟有效期），再由调用方改写。
     *
     * @param customizer 声明改写
     * @return 已签名的 RS256 令牌
     */
    String token(Consumer<JWTClaimsSet.Builder> customizer) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID())
                    .type(JOSEObjectType.JWT).build(), defaultClaims(customizer));
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 用同一把 RSA 私钥签发的 RS512 令牌，声明与正常用户令牌相同；资源服务器只收 RS256，应拒绝。
     *
     * @param userId 业务用户 ID
     * @return 已签名的 RS512 令牌
     */
    String rs512Token(String userId) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS512).keyID(signingKey.getKeyID())
                    .type(JOSEObjectType.JWT).build(), defaultClaims(claims -> claims.claim("user_id", userId)));
            jwt.sign(new RSASSASigner(signingKey));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 用对称密钥签发的 HS256 令牌，声明与正常用户令牌相同；资源服务器只收 RS256，应拒绝。
     *
     * @param userId 业务用户 ID
     * @return 已签名的 HS256 令牌
     */
    String hs256Token(String userId) {
        try {
            byte[] secret = new byte[32];
            new SecureRandom().nextBytes(secret);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(),
                    defaultClaims(claims -> claims.claim("user_id", userId)));
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JWTClaimsSet defaultClaims(Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience("github-api")
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("azp", "github-gateway")
                .claim("typ", "Bearer");
        customizer.accept(claims);
        return claims.build();
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    @Override
    public void close() throws Exception {
        server.close();
    }
}
