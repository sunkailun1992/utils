package com.kellen.security.oauth2;

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.spring.ReferenceBean;
import org.apache.dubbo.config.spring.beans.factory.annotation.ReferenceAnnotationBeanPostProcessor;
import org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

/**
 * 资源服务器的业务上下文来源：本服务不提供 {@link SecurityContextRpcService} 时，经 Dubbo 引用 user 服务的实现。
 *
 * <p>启用条件与 {@link FleetResourceServerAutoConfiguration} 相同，另要求 Dubbo 已启用（{@code @EnableDubbo} 或 Dubbo 自动配置
 * 登记了引用注解处理器）；没有引用也没有本地实现时，令牌用户按不存在处理（401）。</p>
 *
 * <p>单独成为自动配置并排在 Dubbo 自动配置之后，不写成资源服务器自动配置里的嵌套配置：消费服务的主类在 {@code com.kellen} 包，
 * 默认组件扫描覆盖 utils，{@code AutoConfigurationExcludeFilter} 只挡住自动配置类本身。嵌套配置会被当作普通配置提前扫描处理，
 * 早于 {@code @EnableDubbo} 登记引用注解处理器，条件不成立、引用建不出来。Spring Boot 要求自动配置只由 imports 文件加载，
 * 不作为组件扫描的目标。</p>
 *
 * @author 孙凯伦
 */
@AutoConfiguration(after = DubboAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({BearerTokenAuthenticationToken.class, ReferenceBean.class})
@ConditionalOnProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")
@ConditionalOnBean(ReferenceAnnotationBeanPostProcessor.class)
public class FleetSecurityContextDubboAutoConfiguration {

    /**
     * 由 Spring Boot 按自动配置加载。
     */
    public FleetSecurityContextDubboAutoConfiguration() {
    }

    /**
     * user 服务的业务上下文接口；参数沿用各服务既有引用的写法：启动时不初始化（{@code init = false}，Dubbo 默认在启动时初始化
     * 全部引用，没配注册中心的上下文会因此起不来），第一次调用才初始化并连接，不在启动时检查提供方，不重试，3 秒超时，跟随灰度路由。
     * 初始化失败（如没有注册中心）与调用失败一样，由转换器按 user 服务不可用返回 503。
     *
     * @return Dubbo 引用
     */
    @Bean
    @ConditionalOnMissingBean(SecurityContextRpcService.class)
    @DubboReference(init = false, check = false, lazy = true, timeout = 3000, retries = 0,
            parameters = {"traffic.canary.enabled", "true"})
    public ReferenceBean<SecurityContextRpcService> securityContextRpcService() {
        return new ReferenceBean<>();
    }
}
