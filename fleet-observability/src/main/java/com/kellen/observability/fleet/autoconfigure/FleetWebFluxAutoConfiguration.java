package com.kellen.observability.fleet.autoconfigure;

import com.kellen.observability.fleet.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Reactive-only 条件避免同时安装 Servlet 与 WebFlux 两套访问日志。 */
@AutoConfiguration(after = FleetObservabilityAutoConfiguration.class)
@ConditionalOnClass(name = {"org.springframework.web.server.WebFilter", "reactor.core.publisher.Mono"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "fleet.observability", name = "enabled", havingValue = "true")
public class FleetWebFluxAutoConfiguration {
    @Bean FleetWebFluxFilter fleetHttpAccessWebFilter(FleetObservabilityProperties properties, SafeBodyProjector projector, FleetEventSink sink) {
        return new FleetWebFluxFilter(properties, projector, sink);
    }
}
