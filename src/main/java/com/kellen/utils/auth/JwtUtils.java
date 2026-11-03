package com.kellen.utils.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.core.env.Environment;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类。
 *
 * @author 孙凯伦
 */
public final class JwtUtils {

    private JwtUtils() {
    }


    /**
     * 默认过期时间：1天。
     */
    public static final long DEFAULT_EXPIRE_TIME = 24 * 60 * 60 * 1000L;

    /**
     * 当前 Spring 环境，密钥仅在使用时读取，不缓存默认口令。
     */
    private static volatile Environment environment;

    /**
     * 默认签发人。
     */
    @Deprecated
    public static final String ISSUER = "kellen";

    static void configure(Environment configuredEnvironment) {
        environment = configuredEnvironment;
    }

    public static String issuer() {
        Environment current = environment;
        String value = current == null ? null : current.getProperty("security.jwt.issuer");
        return value == null || value.isBlank() ? "kellen" : value;
    }

    /**
     * 由字符串生成签名密钥。
     *
     * @return 签名密钥
     */
    public static SecretKey generalKey() {
        Environment current = environment;
        String encoded = current == null ? null : current.getProperty("security.jwt.secret");
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("security.jwt.secret 未配置");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("security.jwt.secret 必须使用有效 Base64");
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("security.jwt.secret 解码后至少需要 32 字节");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    /**
     * 创建JWT。
     *
     * @param id      JWT唯一标识
     * @param subject JWT主体，通常放用户ID
     * @param claims  业务声明
     * @return JWT字符串
     */
    public static String createJwt(String id, String subject, Map<String, Object> claims) {
        return createJwt(id, subject, claims, DEFAULT_EXPIRE_TIME);
    }

    /**
     * 按指定过期时间创建JWT。
     *
     * @param id           JWT唯一标识
     * @param subject      JWT主体，通常放用户ID
     * @param claims       业务声明
     * @param expireMillis 过期毫秒数
     * @return JWT字符串
     */
    public static String createJwt(String id, String subject, Map<String, Object> claims, long expireMillis) {
        return createJwt(id, subject, claims, expireMillis, issuer());
    }

    /**
     * 按指定过期时间和签发人创建JWT。
     *
     * @param id           JWT唯一标识
     * @param subject      JWT主体，通常放用户ID
     * @param claims       业务声明
     * @param expireMillis 过期毫秒数
     * @param issuer       签发人
     * @return JWT字符串
     */
    public static String createJwt(String id, String subject, Map<String, Object> claims, long expireMillis, String issuer) {
        if (expireMillis <= 0) {
            throw new IllegalArgumentException("expireMillis must be greater than zero");
        }
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256; // 使用 HS256 对称签名算法。

        long nowMillis = System.currentTimeMillis(); // 记录当前时间戳。
        Date now = new Date(nowMillis); // 生成 JWT 签发时间。

        SecretKey key = generalKey(); // 生成签名密钥。

        JwtBuilder builder = Jwts.
                builder() // 创建 JWT 构造器。
                .setClaims(claims == null ? new java.util.HashMap<>() : new java.util.HashMap<>(claims)) // 写入业务声明，必须在标准声明前设置，避免覆盖标准声明。
                .setId(id) // 写入 jti，便于后续做重放保护或日志定位。
                .setIssuedAt(now) // 写入签发时间。
                .setIssuer(issuer == null || issuer.isBlank() ? issuer() : issuer) // 写入签发人。
                .setSubject(subject) // 写入主体，一般为用户ID。
                .setExpiration(new Date(nowMillis + expireMillis)) // 写入过期时间。
                .signWith(signatureAlgorithm, key); // 写入签名算法和密钥。

        return builder.compact(); // 压缩生成最终 JWT 字符串。
    }


    /**
     * 解析JWT。
     *
     * @param jwt JWT字符串
     * @return JWT声明
     */
    public static Claims parseJwt(String jwt) {
        SecretKey key = generalKey(); // 使用与签发时一致的密钥验签。
        Claims claims = Jwts.
                parser() // 创建 JWT 解析器。
                .setSigningKey(key) // 设置验签密钥。
                .parseClaimsJws(jwt).getBody(); // 解析并返回载荷声明。
        return claims; // 返回解析后的声明。
    }
}
