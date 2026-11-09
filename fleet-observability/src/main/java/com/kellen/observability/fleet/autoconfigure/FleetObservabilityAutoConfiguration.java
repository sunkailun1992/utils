package com.kellen.observability.fleet.autoconfigure;

import com.kellen.observability.fleet.FleetEventSink;
import com.kellen.observability.fleet.FleetObservabilityProperties;
import com.kellen.observability.fleet.SafeBodyProjector;
import com.kellen.observability.fleet.Slf4jFleetEventSink;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 默认关闭的共享安全策略，Web 适配由各自实际 classpath 条件装配。 */
@AutoConfiguration
@EnableConfigurationProperties(FleetObservabilityProperties.class)
@ConditionalOnProperty(prefix = "fleet.observability", name = "enabled", havingValue = "true")
public class FleetObservabilityAutoConfiguration {
    @Bean @ConditionalOnMissingBean SafeBodyProjector fleetSafeBodyProjector(FleetObservabilityProperties properties) { return new SafeBodyProjector(properties); }
    @Bean @ConditionalOnMissingBean FleetEventSink fleetEventSink() { return new Slf4jFleetEventSink(); }
}
