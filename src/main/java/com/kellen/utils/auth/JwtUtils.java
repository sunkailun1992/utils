package com.kellen.utils.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.apache.commons.codec.binary.Base64;

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
     * 默认过期时间：30分钟。
     */
    private static final long EXPIRE_TIME = 30 * 60 * 1000;

    /**
     * 默认签名密钥。
     */
    public static final String KEY = "skl19921210";

    /**
     * 默认签发人。
     */
    public static final String ISSUER = "skl";

    /**
     * 由字符串生成签名密钥。
     *
     * @return 签名密钥
     */
    public static SecretKey generalKey() {
        byte[] encodedKey = Base64.decodeBase64(KEY); // 将配置密钥按 Base64 解码为字节数组。
        SecretKeySpec key = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES"); // 使用 AES 密钥格式兼容旧 jjwt 签名逻辑。
        return key; // 返回签名密钥。
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

        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256; // 使用 HS256 对称签名算法。

        long nowMillis = System.currentTimeMillis(); // 记录当前时间戳。
        Date now = new Date(nowMillis); // 生成 JWT 签发时间。

        SecretKey key = generalKey(); // 生成签名密钥。

        JwtBuilder builder = Jwts.
                builder() // 创建 JWT 构造器。
                .setClaims(claims) // 写入业务声明，必须在标准声明前设置，避免覆盖标准声明。
                .setId(id) // 写入 jti，便于后续做重放保护或日志定位。
                .setIssuedAt(now) // 写入签发时间。
                .setIssuer(ISSUER) // 写入签发人。
                .setSubject(subject) // 写入主体，一般为用户ID。
                .setExpiration(new Date(nowMillis + EXPIRE_TIME)) // 写入过期时间。
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
