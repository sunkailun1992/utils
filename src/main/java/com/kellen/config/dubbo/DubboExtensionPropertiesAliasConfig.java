package com.kellen.config.dubbo;

import com.kellen.security.config.TenantProperties;
import com.kellen.traffic.TrafficGovernanceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;

/**
 * 让 Dubbo 扩展按 setter 名在 Spring 容器里找到已绑定的配置属性。
 *
 * <p>Dubbo 创建 {@link DubboContextPropagationFilter} 这类扩展后，按 setter 推出的名字（如
 * {@code setTrafficGovernanceProperties} → {@code trafficGovernanceProperties}）到 Spring 容器取 Bean，不按类型查找。
 * {@code @EnableConfigurationProperties} 注册的 Bean 名形如 {@code traffic.governance-com.kellen.traffic.TrafficGovernanceProperties}，
 * 对不上时 Dubbo 静默跳过注入，过滤器只能用默认值：灰度关闭、没有签名密钥、灰度实例不要求授权、租户头名取默认值。</p>
 *
 * <p>这里只给容器里唯一的同类型 Bean 补一个别名，不新增 Bean，按类型注入的地方不受影响；已有同名 Bean 或别名时保持不动。
 * 同类型 Bean 不止一个时无法判断该用哪个，记告警后不补别名。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class DubboExtensionPropertiesAliasConfig {

    /**
     * Dubbo 扩展 setter 对应的 Bean 名，及其配置属性类型。
     */
    private static final Map<String, Class<?>> ALIASES = Map.of(
            "trafficGovernanceProperties", TrafficGovernanceProperties.class,
            "tenantProperties", TenantProperties.class);

    /**
     * 在 Bean 定义全部注册完、单例创建之前补齐别名；Dubbo 创建扩展发生在这之后。
     *
     * @return 补别名的 Bean 工厂后置处理器
     */
    @Bean
    static BeanFactoryPostProcessor dubboExtensionPropertiesAliases() {
        return beanFactory -> ALIASES.forEach((alias, type) -> registerAlias(beanFactory, alias, type));
    }

    private static void registerAlias(ConfigurableListableBeanFactory beanFactory, String alias, Class<?> type) {
        if (beanFactory.containsBean(alias)) {
            return;
        }
        String[] names = beanFactory.getBeanNamesForType(type, true, false);
        if (names.length == 1) {
            beanFactory.registerAlias(names[0], alias);
        } else if (names.length > 1) {
            log.warn("Dubbo 扩展注入跳过：{} 有多个 Bean {}，无法确定给 {} 用哪个", type.getName(), Arrays.toString(names), alias);
        }
    }
}
