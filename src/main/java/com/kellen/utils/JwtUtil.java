package com.kellen.utils;

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
 * jwt工具类
 *
 * @author 孙凯伦
 * @DateTime 2020/12/29  上午11:14
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class JwtUtil {
    /**
     * 默认30分钟
     */
    private static final long EXPIRE_TIME = 30 * 60 * 1000;
    /**
     * 密钥 -- 根据实际项目，这里可以做成配置
     */
    public static final String KEY = "skl19921210";
    /**
     * 签发人
     */
    public static final String ISSUER = "skl";

    /**
     * 由字符串生成加密key
     *
     * @return
     */
    public static SecretKey generalKey() {
        byte[] encodedKey = Base64.decodeBase64(KEY);
        SecretKeySpec key = new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES");
        return key;
    }

    /**
     * 创建jwt
     *
     * @param id      设置jti(JWT ID)：是JWT的唯一标识，根据业务需要，这个可以设置为一个不重复的值，主要用来作为一次性token,从而回避重放攻击。
     * @param subject 代表这个JWT的主体，即它的所有人，这个是一个json格式的字符串，可以存放什么userid，roldid之类的，作为什么用户的唯一标志。
     * @param claims  创建payload的私有声明（根据特定的业务需要添加，如果要拿这个做验证，一般是需要和jwt的接收方提前沟通好验证方式的）
     * @return
     * @throws Exception
     */
    public static String createJwt(String id, String subject, Map<String, Object> claims) {

        // 指定签名的时候使用的签名算法，也就是header那部分，jjwt已经将这部分内容封装好了。
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;

        // 生成JWT的时间
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);

        // 生成签名的时候使用的秘钥secret，切记这个秘钥不能外露哦。它就是你服务端的私钥，在任何场景都不应该流露出去。
        // 一旦客户端得知这个secret, 那就意味着客户端是可以自我签发jwt了。
        SecretKey key = generalKey();

        // 下面就是在为payload添加各种标准声明和私有声明了
        JwtBuilder builder = Jwts.
                // 这里其实就是new一个JwtBuilder，设置jwt的body
                        builder()
                // 如果有私有声明，一定要先设置这个自己创建的私有的声明，这个是给builder的claim赋值，一旦写在标准的声明赋值之后，就是覆盖了那些标准的声明的
                .setClaims(claims)
                // 设置jti(JWT ID)：是JWT的唯一标识，根据业务需要，这个可以设置为一个不重复的值，主要用来作为一次性token,从而回避重放攻击。
                .setId(id)
                // iat: jwt的签发时间
                .setIssuedAt(now)
                // issuer：jwt签发人
                .setIssuer(ISSUER)
                // sub(Subject)：代表这个JWT的主体，即它的所有人，这个是一个json格式的字符串，可以存放什么userid，roldid之类的，作为什么用户的唯一标志。
                .setSubject(subject)
                // 设置签名使用的签名算法和签名使用的秘钥
                .signWith(signatureAlgorithm, key);

        // 设置过期时间
//        if (EXPIRE_TIME >= 0) {
//            long expMillis = nowMillis + EXPIRE_TIME;
//            Date exp = new Date(expMillis);
//            builder.setExpiration(exp);
//        }
        return builder.compact();
    }


    /**
     * 解密jwt
     *
     * @param jwt
     * @return
     * @throws Exception
     */
    public static Claims parseJwt(String jwt) {
        //签名秘钥，和生成的签名的秘钥一模一样
        SecretKey key = generalKey();
        Claims claims = Jwts.
                //得到DefaultJwtParser
                        parser()
                //设置签名的秘钥
                .setSigningKey(key)
                //设置需要解析的jwt
                .parseClaimsJws(jwt).getBody();
        return claims;
    }

    public static void main(String[] args) {
//        Map<String, Object> claims = Maps.newHashMap();
//        claims.put("user", new User());
//        String jwt = JwtUtil.createJWT(StringUtils.getUUID(),"skl","1",claims);
        Claims c = parseJwt("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMzQyNzMwMjc1NjQ2MDkxMjY1IiwiYXZhdGFyVXJsIjpudWxsLCJpc3MiOiJ0ZXN0IiwiZXhwIjoxNjA5MjIzMjQyLCJpYXQiOjE2MDkyMjE0NDIsImp0aSI6IjM5MDJlOTYxMDY0OTQ4ZDY4ODBjYmQ2OTc3NTZmYWZkIiwidXNlcm5hbWUiOiIxMSJ9.6S7Ygta1or6bYTrLL70DSf8Ht1UV6de29g92dBB7RJQ");

        System.out.printf(c.getSubject());
    }
}
