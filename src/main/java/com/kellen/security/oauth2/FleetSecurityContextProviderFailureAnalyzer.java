package com.kellen.security.oauth2;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * 把「资源服务器没有业务上下文提供方」的启动失败写成 Spring Boot 的 APPLICATION FAILED TO START 说明（原因与做法），
 * 经 {@code META-INF/spring.factories} 登记，做法同 Spring Boot 自带的启动失败分析器。
 *
 * @author 孙凯伦
 */
class FleetSecurityContextProviderFailureAnalyzer extends AbstractFailureAnalyzer<FleetSecurityContextProviderMissingException> {

    /**
     * 由 Spring Boot 按 spring.factories 创建。
     */
    FleetSecurityContextProviderFailureAnalyzer() {
    }

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, FleetSecurityContextProviderMissingException cause) {
        return new FailureAnalysis(cause.getMessage(),
                "启用 Dubbo（@EnableDubbo 或 Dubbo 的 Spring Boot 自动配置），utils 会自动建 user 服务 SecurityContextRpcService 的引用；"
                        + "或者在本服务实现 SecurityContextRpcService 并标 @Primary（user 服务的做法）。不接收用户令牌的服务不要配 "
                        + "spring.security.oauth2.resourceserver.jwt.issuer-uri。",
                cause);
    }
}
