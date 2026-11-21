package com.kellen.security.oauth2.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 测试用的签发方替身：在本机回环的随机端口提供 OIDC 发现文档与公钥集，签发 RS256 访问令牌，
 * 让资源服务器按 Spring Boot 真实的 issuer-uri 发现流程校验签名、签发方、受众与有效期，只把对端换成本地进程。
 * 签发方地址与正式环境的 Keycloak 域同形（{@value #REALM_PATH}）。
 *
 * <p>utils 以 Gradle 测试夹具（{@code java-test-fixtures}）发布，消费者在测试依赖里写
 * {@code testImplementation "com:utils:<版本>:test-fixtures"}，不再各自复制。只用 JDK 自带的 HTTP 服务与
 * Nimbus（资源服务器本来就带），不给消费者增加测试依赖。</p>
 *
 * @author 孙凯伦
 */
public final class FakeIssuer implements AutoCloseable {

    /**
     * 测试域路径，与正式环境的 github-dev 域同名。
     */
    public static final String REALM_PATH = "/realms/github-dev";

    private final HttpServer server;

    private final RSAKey signingKey;

    private final JWSSigner rsaSigner;

    private FakeIssuer() throws IOException, JOSEException {
        // 公钥不写 alg：资源服务器若按公钥集推断算法会放行 RS256/RS384/RS512，只有显式限定 RS256 才会拒绝 RS512。
        signingKey = new RSAKeyGenerator(2048).keyID("test-signing-key").keyUse(KeyUse.SIGNATURE).generate();
        rsaSigner = new RSASSASigner(signingKey);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(REALM_PATH + "/.well-known/openid-configuration", exchange -> json(exchange, discovery()));
        server.createContext(REALM_PATH + "/protocol/openid-connect/certs",
                exchange -> json(exchange, new JWKSet(signingKey.toPublicJWK()).toString()));
        server.start();
    }

    /**
     * 启动替身。
     *
     * @return 已启动的替身
     */
    public static FakeIssuer start() {
        try {
            return new FakeIssuer();
        } catch (IOException | JOSEException ex) {
            throw new IllegalStateException("测试签发方替身启动失败", ex);
        }
    }

    /**
     * 签发方地址（资源服务器的 issuer-uri），与令牌里的 iss 一致。
     *
     * @return 签发方地址
     */
    public String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + REALM_PATH;
    }

    /**
     * 浏览器经网关登录后拿到的用户令牌：受众 github-api、azp=github-gateway、带 user_id。
     *
     * @param userId 业务用户 ID
     * @return 已签名的令牌
     */
    public String userToken(String userId) {
        return token(claims -> claims.claim("user_id", userId));
    }

    /**
     * 客户端凭证方式拿到的机器令牌：没有 user_id，azp 为客户端 ID。
     *
     * @param clientId 客户端 ID
     * @return 已签名的令牌
     */
    public String machineToken(String clientId) {
        return token(claims -> claims.claim("azp", clientId));
    }

    /**
     * 指定受众与 azp 的用户令牌。
     *
     * @param userId   业务用户 ID
     * @param audience 受众
     * @param azp      签发给哪个客户端
     * @return 已签名的令牌
     */
    public String token(String userId, String audience, String azp) {
        return token(claims -> claims.audience(List.of(audience)).claim("azp", azp).claim("user_id", userId));
    }

    /**
     * ai 经令牌交换（RFC 8693）取得的令牌形状：受众 github-ai-agent、azp=github-ai、带同一 user_id。
     *
     * @param userId 业务用户 ID
     * @return 已签名的令牌
     */
    public String exchangedToken(String userId) {
        return token(userId, "github-ai-agent", "github-ai");
    }

    /**
     * 签发 RS256 令牌：先填默认声明（受众 github-api、azp=github-gateway、5 分钟有效期、随机 sub 与 jti），再由调用方改写。
     *
     * @param customizer 声明改写
     * @return 已签名的令牌
     */
    public String token(Consumer<JWTClaimsSet.Builder> customizer) {
        return sign(JWSAlgorithm.RS256, rsaSigner, customizer);
    }

    /**
     * 用同一把 RSA 私钥签发的 RS512 令牌，声明与正常用户令牌相同；只收 RS256 的资源服务器应拒绝。
     *
     * @param userId 业务用户 ID
     * @return 已签名的令牌
     */
    public String rs512Token(String userId) {
        return sign(JWSAlgorithm.RS512, rsaSigner, claims -> claims.claim("user_id", userId));
    }

    /**
     * 用随机对称密钥签发的 HS256 令牌，声明与正常用户令牌相同；只收 RS256 的资源服务器应拒绝。
     *
     * @param userId 业务用户 ID
     * @return 已签名的令牌
     */
    public String hs256Token(String userId) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        try {
            return sign(JWSAlgorithm.HS256, new MACSigner(secret), claims -> claims.claim("user_id", userId));
        } catch (KeyLengthException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private String sign(JWSAlgorithm algorithm, JWSSigner signer, Consumer<JWTClaimsSet.Builder> customizer) {
        JWSHeader.Builder header = new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT);
        if (!JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            header.keyID(signingKey.getKeyID());
        }
        try {
            SignedJWT jwt = new SignedJWT(header.build(), defaultClaims(customizer));
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private JWTClaimsSet defaultClaims(Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer())
                .audience("github-api")
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .jwtID(UUID.randomUUID().toString())
                .claim("azp", "github-gateway")
                .claim("typ", "Bearer");
        customizer.accept(claims);
        return claims.build();
    }

    private String discovery() {
        String issuer = issuer();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/protocol/openid-connect/auth");
        metadata.put("token_endpoint", issuer + "/protocol/openid-connect/token");
        metadata.put("userinfo_endpoint", issuer + "/protocol/openid-connect/userinfo");
        metadata.put("end_session_endpoint", issuer + "/protocol/openid-connect/logout");
        metadata.put("jwks_uri", issuer + "/protocol/openid-connect/certs");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put("subject_types_supported", List.of("public"));
        metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
        metadata.put("code_challenge_methods_supported", List.of("S256"));
        return JSONObjectUtils.toJSONString(metadata);
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
