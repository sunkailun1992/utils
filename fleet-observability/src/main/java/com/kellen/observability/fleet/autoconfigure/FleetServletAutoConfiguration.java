package com.kellen.observability.fleet.autoconfigure;

import com.kellen.observability.fleet.*;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Servlet-only 条件阻止轻量 WebFlux 消费者加载 MVC/Servlet 适配类。 */
@AutoConfiguration(after = FleetObservabilityAutoConfiguration.class)
@ConditionalOnClass(name = {"jakarta.servlet.Filter", "org.springframework.web.servlet.HandlerMapping"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "fleet.observability", name = "enabled", havingValue = "true")
public class FleetServletAutoConfiguration {
    @Bean FilterRegistrationBean<FleetServletFilter> fleetHttpAccessFilter(FleetObservabilityProperties properties, SafeBodyProjector projector, FleetEventSink sink) {
        var registration = new FilterRegistrationBean<>(new FleetServletFilter(properties, projector, sink));
        registration.setName("fleetHttpAccessFilter"); registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setAsyncSupported(true); registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
}
