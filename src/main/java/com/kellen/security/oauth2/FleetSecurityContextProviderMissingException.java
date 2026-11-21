package com.kellen.security.oauth2;

import java.io.Serial;

/**
 * 资源服务器启动时没有业务上下文提供方（{@link SecurityContextRpcService}）：既没有本地实现，也没有 Dubbo 引用。
 * 由 {@link FleetSecurityContextProviderFailureAnalyzer} 转成 Spring Boot 的启动失败说明。
 *
 * @author 孙凯伦
 */
class FleetSecurityContextProviderMissingException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造异常。
     *
     * @param message 说明
     */
    FleetSecurityContextProviderMissingException(String message) {
        super(message);
    }
}
