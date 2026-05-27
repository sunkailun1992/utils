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
 * OpenAPI文档配置。
 *
 * @author sunkailun
 */
@Configuration
public class Swagger {

    /**
     * Spring应用上下文。
     */
    private final ConfigurableApplicationContext applicationContext;

    /**
     * 构造OpenAPI文档配置。
     *
     * @param applicationContext Spring应用上下文
     */
    public Swagger(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext; // 保存上下文，用于读取应用名称和版本配置。
    }

    /**
     * 创建OpenAPI文档对象。
     *
     * @return OpenAPI文档对象
     */
    @Bean
    public OpenAPI openAPI() {
        String name = Optional.ofNullable(applicationContext.getEnvironment().getProperty("swagger.name")).orElse(""); // 读取服务文档名称。
        String version = Optional.ofNullable(applicationContext.getEnvironment().getProperty("version")).orElse(""); // 读取服务版本，仅作为文档信息展示。
        return new OpenAPI()
                .info(new Info()
                        .title(name)
                        .description(name)
                        .version(version)
                        .contact(new Contact()
                                .name("孙凯伦")
                                .url("https://github.com/sunkailun1992")
                                .email("kl19921210@gmail.com")))
                .schemaRequirement("Authorization", bearerAuth())
                .schemaRequirement("dataSource", apiKey("dataSource"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("Authorization")
                        .addList("dataSource"));
    }

    /**
     * 创建请求头API Key安全定义。
     *
     * @param name 请求头名称
     * @return OpenAPI安全定义
     */
    private SecurityScheme apiKey(String name) {
        return new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(name); // dataSource 仍作为动态数据源请求头展示。
    }

    /**
     * 创建Bearer JWT安全定义。
     *
     * @return OpenAPI安全定义
     */
    private SecurityScheme bearerAuth() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"); // 新认证体系使用 Authorization: Bearer <jwt>，不再展示历史 token 请求头。
    }
}
