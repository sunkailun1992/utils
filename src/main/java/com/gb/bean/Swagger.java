package com.gb.bean;

import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import springfox.bean.validators.configuration.BeanValidatorPluginsConfiguration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2018/7/27  11:47 AM
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Configuration
@Import(BeanValidatorPluginsConfiguration.class)
public class Swagger {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    private final OpenApiExtensionResolver openApiExtensionResolver;

    @Autowired
    public Swagger(OpenApiExtensionResolver openApiExtensionResolver) {
        this.openApiExtensionResolver = openApiExtensionResolver;
    }

    @Bean(value = "defaultApi2")
    public Docket defaultApi2() {
        return new Docket(DocumentationType.OAS_30)
                .enable( Boolean.parseBoolean(applicationContext.getEnvironment().getProperty("swagger.enable")))
                .apiInfo(apiInfo())
                .select()
                //为当前包路径
                .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class))
                .paths(PathSelectors.any())
                .build()
                .extensions(openApiExtensionResolver.buildExtensions(applicationContext.getEnvironment().getProperty("swagger.name")))
                .extensions(openApiExtensionResolver.buildSettingExtensions())
                .securitySchemes(securitySchemes())
                .securityContexts(securityContexts());
    }

    /**
     * 构建 api文档的详细信息函数,注意这里的注解引用的是哪个Docket
     * @return
     */
    private ApiInfo apiInfo() {
        Contact contact = new Contact("孙凯伦", "https://github.com/sunkailun1992", "kl19921210@gmail.com");
        return new ApiInfoBuilder()
                //页面标题
                .title("工保网:"+applicationContext.getEnvironment().getProperty("swagger.name"))
                //创建人
                .contact(contact)
                //版本号
                .version(applicationContext.getEnvironment().getProperty("version"))
                //描述
                .description(applicationContext.getEnvironment().getProperty("swagger.name"))
                .build();
    }


    private List<SecurityScheme> securitySchemes() {
        List<SecurityScheme> securitySchemes = new ArrayList<>();
        securitySchemes.add(new ApiKey("token", "token", "header"));
        securitySchemes.add(new ApiKey("version", "version", "header"));
        securitySchemes.add(new ApiKey("dataSource", "dataSource", "header"));
        return securitySchemes;
    }

    private List<SecurityContext> securityContexts() {
        List<SecurityContext> securityContexts = new ArrayList<>();
        securityContexts.add(SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.regex("^(?!auth).*$")).build());
        return securityContexts;
    }

    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        List<SecurityReference> securityReferences = new ArrayList<>();
        securityReferences.add(new SecurityReference("token", authorizationScopes));
        securityReferences.add(new SecurityReference("version", authorizationScopes));
        securityReferences.add(new SecurityReference("dataSource", authorizationScopes));
        return securityReferences;
    }

}
