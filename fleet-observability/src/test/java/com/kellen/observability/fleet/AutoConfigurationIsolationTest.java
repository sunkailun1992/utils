package com.kellen.observability.fleet;

import com.kellen.observability.fleet.autoconfigure.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

/** 模拟只有一种 Web 栈的消费者，不能因可选适配器 class loading 引入另一套依赖。 */
class AutoConfigurationIsolationTest {
    private final AutoConfigurations configurations = AutoConfigurations.of(FleetObservabilityAutoConfiguration.class,
            FleetServletAutoConfiguration.class, FleetWebFluxAutoConfiguration.class);
    @Test void defaultOffDoesNotInstallAnyLoggingHooks() {
        new ApplicationContextRunner().withConfiguration(configurations).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(FleetEventSink.class).doesNotHaveBean(SafeBodyProjector.class);
        });
    }
    @Test void servletOnlyClasspathDoesNotResolveReactiveClasses() {
        new WebApplicationContextRunner().withConfiguration(configurations).withPropertyValues("fleet.observability.enabled=true")
                .withClassLoader(new FilteredClassLoader("org.springframework.web.reactive", "reactor", "org.reactivestreams"))
                .run(context -> assertThat(context).hasNotFailed().hasBean("fleetHttpAccessFilter").doesNotHaveBean("fleetHttpAccessWebFilter"));
    }
    @Test void reactiveOnlyClasspathDoesNotResolveServletClasses() {
        new ReactiveWebApplicationContextRunner().withConfiguration(configurations).withPropertyValues("fleet.observability.enabled=true")
                .withClassLoader(new FilteredClassLoader("jakarta.servlet", "org.springframework.web.servlet"))
                .run(context -> assertThat(context).hasNotFailed().hasBean("fleetHttpAccessWebFilter").doesNotHaveBean("fleetHttpAccessFilter"));
    }
}
