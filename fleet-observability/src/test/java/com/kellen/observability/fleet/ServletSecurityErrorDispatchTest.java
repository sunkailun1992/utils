package com.kellen.observability.fleet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** 真正 Tomcat + Security ERROR 再次拒绝；不能改为放行 /error 来规避终态缺失。 */
class ServletSecurityErrorDispatchTest {
    private static final String TRACE = "0123456789abcdef0123456789abcdef";
    private static final String SPAN = "0123456789abcdef";
    private static ConfigurableApplicationContext application;
    private static final List<Map<String, Object>> EVENTS = new CopyOnWriteArrayList<>();
    private static final List<DispatcherType> DISPATCHES = new CopyOnWriteArrayList<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @BeforeAll static void start() {
        application = new SpringApplicationBuilder(App.class).web(WebApplicationType.SERVLET).properties(Map.of(
                "server.port", "0", "server.address", "127.0.0.1", "spring.main.banner-mode", "off",
                "logging.level.root", "OFF", "fleet.observability.enabled", "true",
                "spring.main.register-shutdown-hook", "false")).run();
    }
    @AfterAll static void stop() { if (application != null) application.close(); }
    @BeforeEach void clear() { EVENTS.clear(); DISPATCHES.clear(); }

    @Test void missingAuthenticationHasOneTerminalAfterSecurityRejectsErrorDispatch() throws Exception {
        assertRejected(false);
    }
    @Test void wrongAuthenticationHasOneTerminalAfterSecurityRejectsErrorDispatch() throws Exception {
        assertRejected(true);
    }

    private void assertRejected(boolean wrongAuthentication) throws Exception {
        int port = Integer.parseInt(application.getEnvironment().getProperty("local.server.port"));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/synthetic/protected"))
                .timeout(Duration.ofSeconds(5));
        if (wrongAuthentication) request.header("Authorization", "Bearer SYNTHETIC_SECRET");
        var response = CLIENT.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEmpty();
        assertThat(Collections.frequency(DISPATCHES, DispatcherType.REQUEST)).isEqualTo(1);
        assertThat(Collections.frequency(DISPATCHES, DispatcherType.ERROR)).isEqualTo(1);
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (EVENTS.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
        assertThat(EVENTS).hasSize(1);
        assertThat(EVENTS.get(0)).containsEntry("http_status", 403).containsEntry("outcome", "rejected")
                .containsEntry("response_bytes", 0L).containsEntry("response_body_complete", true)
                .containsEntry("trace_id", TRACE).containsEntry("span_id", SPAN)
                .doesNotContainKeys("request_body", "response_body");
        assertThat(EVENTS.toString()).doesNotContain("SYNTHETIC_SECRET", "Authorization", "/synthetic/protected");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableWebSecurity
    static class App {
        @Bean FleetEventSink fixtureSink() { return event -> EVENTS.add(event); }
        @Bean SecurityFilterChain fixtureSecurity(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable());
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            return http.build();
        }
        @Bean FilterRegistrationBean<Filter> fixtureSecurityRegistration(@Qualifier("springSecurityFilterChain") Filter filter) {
            var registration = new FilterRegistrationBean<>(filter);
            registration.setName("fixtureSecurity"); registration.setOrder(-100); registration.setAsyncSupported(true);
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
            return registration;
        }
        @Bean FilterRegistrationBean<Filter> fixtureTraceRegistration() {
            var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                DISPATCHES.add(request.getDispatcherType());
                var span = Span.wrap(SpanContext.create(TRACE, SPAN, TraceFlags.getSampled(), TraceState.getDefault()));
                try (var ignored = span.makeCurrent()) { chain.doFilter(request, response); }
            });
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); registration.setAsyncSupported(true);
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
            return registration;
        }
    }
}
