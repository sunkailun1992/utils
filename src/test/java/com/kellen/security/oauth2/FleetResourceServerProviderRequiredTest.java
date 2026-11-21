package com.kellen.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.diagnostics.FailureAnalyzer;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 资源服务器离不开业务上下文：配了 issuer-uri 却既没有本地 {@link SecurityContextRpcService}、也没有 Dubbo 引用时启动失败，
 * 并由 Spring Boot 的启动失败分析说明原因与做法；以前会照常启动，之后每个用户令牌都被当成用户不存在。
 */
class FleetResourceServerProviderRequiredTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FleetResourceServerAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class))
            .withPropertyValues("spring.security.oauth2.resourceserver.jwt.issuer-uri=http://127.0.0.1:1/realms/github-dev");

    @Test
    void startupFailsWithoutAContextProvider() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(FleetSecurityContextProviderMissingException.class)
                    .hasMessageContaining("SecurityContextRpcService");
        });
    }

    @Test
    void failureAnalysisSaysWhatIsMissingAndWhatToDo() {
        runner.run(context -> {
            FailureAnalysis analysis = new FleetSecurityContextProviderFailureAnalyzer().analyze(context.getStartupFailure());
            assertThat(analysis).isNotNull();
            assertThat(analysis.getDescription()).contains("SecurityContextRpcService");
            assertThat(analysis.getAction()).contains("@EnableDubbo").contains("@Primary");
        });
    }

    /**
     * 按 Spring Boot 加载启动失败分析器的方式（spring.factories，构造参数可取 BeanFactory 与 Environment）找得到本分析器。
     */
    @Test
    void failureAnalyzerIsRegisteredForSpringBoot() {
        SpringFactoriesLoader.ArgumentResolver arguments = SpringFactoriesLoader.ArgumentResolver
                .of(BeanFactory.class, new DefaultListableBeanFactory()).and(Environment.class, new StandardEnvironment());
        assertThat(SpringFactoriesLoader.forDefaultResourceLocation().load(FailureAnalyzer.class, arguments))
                .hasAtLeastOneElementOfType(FleetSecurityContextProviderFailureAnalyzer.class);
    }

    @Test
    void startsWithALocalProvider() {
        runner.withBean(SecurityContextRpcService.class,
                        () -> (userId, tenantId) -> SecurityContextResult.of(SecurityContextResult.Status.USER_NOT_FOUND))
                .run(context -> assertThat(context).hasNotFailed());
    }
}
