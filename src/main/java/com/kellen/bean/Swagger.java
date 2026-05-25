package com.kellen.bean;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * OpenAPI document configuration.
 *
 * @author sunkailun
 */
@Configuration
public class Swagger {

    private final ConfigurableApplicationContext applicationContext;

    public Swagger(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    public OpenAPI openAPI() {
        String name = Optional.ofNullable(applicationContext.getEnvironment().getProperty("swagger.name")).orElse("");
        String version = Optional.ofNullable(applicationContext.getEnvironment().getProperty("version")).orElse("");
        return new OpenAPI()
                .info(new Info()
                        .title(name)
                        .description(name)
                        .version(version)
                        .contact(new Contact()
                                .name("孙凯伦")
                                .url("https://github.com/sunkailun1992")
                                .email("kl19921210@gmail.com")))
                .schemaRequirement("token", apiKey("token"))
                .schemaRequirement("version", apiKey("version"))
                .schemaRequirement("dataSource", apiKey("dataSource"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("token")
                        .addList("version")
                        .addList("dataSource"));
    }

    private SecurityScheme apiKey(String name) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(name);
    }
}
