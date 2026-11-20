package com.kellen.security.oauth2;

import org.springframework.security.authentication.AuthenticationServiceException;

import java.io.Serial;

/**
 * 令牌有效，但 user 服务暂时给不出业务上下文（没有提供方、超时）：服务端问题，返回 503 而不是 401，
 * 免得前端把它当作登录失效反复跳转登录。结果不进缓存，下次请求重新加载。
 *
 * @author 孙凯伦
 */
public class FleetSecurityContextUnavailableException extends AuthenticationServiceException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 说明
     * @param cause   调用失败的原因
     */
    public FleetSecurityContextUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
