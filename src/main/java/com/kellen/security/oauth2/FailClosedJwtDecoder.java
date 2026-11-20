package com.kellen.security.oauth2;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 签发方暂不可达时按「无法校验令牌」拒绝请求。
 *
 * <p>Spring Boot 按 issuer-uri 创建的是延迟发现的 {@code SupplierJwtDecoder}：首次解码时才取发现文档，失败时抛
 * {@link JwtDecoderInitializationException}（不是 {@link JwtException}），资源服务器不会把它当认证失败，请求会以 500
 * 和堆栈结束。这里把它换成 {@link JwtException}，由标准的 JWT 认证提供者转成认证服务异常、返回 401；发现失败不缓存，
 * 签发方恢复后下一个请求重新发现，无需重启。</p>
 *
 * @author 孙凯伦
 */
final class FailClosedJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;

    /**
     * 包装解码器。
     *
     * @param delegate Spring Boot 创建的解码器
     */
    FailClosedJwtDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Jwt decode(String token) {
        try {
            return delegate.decode(token);
        } catch (JwtDecoderInitializationException ex) {
            throw new JwtException("签发方元数据暂时取不到：" + rootMessage(ex), ex);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
