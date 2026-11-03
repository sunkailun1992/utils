package com.kellen.config.actuator;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Actuator 访问保护测试使用的最小 Web 应用。
 *
 * <p>和消费者一样通过组件扫描装配 utils 的 actuator、web、security 配置，只排除会在启动时直连 Redis 的
 * {@link ConfigClientController} 和测试类内部的专用配置。</p>
 */
@SpringBootConfiguration
@Import(ActuatorInfrastructureTestConfiguration.class)
@ComponentScan(
        basePackages = {"com.kellen.config.actuator", "com.kellen.config.web", "com.kellen.security.config"},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ConfigClientController.class)
        }
)
class ActuatorProtectionTestApplication {

    /**
     * 注册测试专用写端点，用调用次数判断未授权写请求是否真正进入了端点方法。
     *
     * @return 测试写端点
     */
    @Bean
    ProbeWriteEndpoint probeWriteEndpoint() {
        return new ProbeWriteEndpoint();
    }

    /**
     * 测试专用 Actuator 写端点，模拟 refresh、serviceregistry 这类会改变实例状态的写操作。
     */
    @Endpoint(id = "probewrite")
    static class ProbeWriteEndpoint {

        /**
         * 写操作被真正执行的次数。
         */
        private final AtomicInteger invocations = new AtomicInteger();

        /**
         * 记录一次写操作。
         *
         * @return 写入结果
         */
        @WriteOperation
        public Map<String, Object> write() {
            invocations.incrementAndGet();
            return Map.of("written", true);
        }

        /**
         * 返回写操作被执行的次数。
         *
         * @return 执行次数
         */
        int invocations() {
            return invocations.get();
        }
    }
}
