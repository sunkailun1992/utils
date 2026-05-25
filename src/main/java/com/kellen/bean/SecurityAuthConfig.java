package com.kellen.bean;

import com.kellen.security.SecurityAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityAuthProperties.class)
public class SecurityAuthConfig {

    private final SecurityAuthProperties securityAuthProperties;

    public SecurityAuthConfig(SecurityAuthProperties securityAuthProperties) {
        this.securityAuthProperties = securityAuthProperties;
    }

    @Bean
    public SecurityAuthenticationFilter securityAuthenticationFilter() {
        return new SecurityAuthenticationFilter(securityAuthProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.addFilterBefore(securityAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        http.authorizeHttpRequests(registry -> {
            if (!securityAuthProperties.isEnabled()) {
                registry.anyRequest().permitAll();
                return;
            }
            securityAuthProperties.getPermitUrls().forEach(url -> registry.requestMatchers(url).permitAll());
            registry.anyRequest().authenticated();
        });
        return http.build();
    }
}
