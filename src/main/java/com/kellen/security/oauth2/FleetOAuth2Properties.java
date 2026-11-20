package com.kellen.security.oauth2;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * utils 资源服务器与出站 OAuth2 的配置（前缀 {@code fleet.security.oauth2}）。
 *
 * <p>令牌校验本身用 Spring Boot 原生属性 {@code spring.security.oauth2.resourceserver.jwt.*}
 * （issuer-uri、jws-algorithms、audiences），这里只放 utils 自己的扩展项。</p>
 *
 * @author 孙凯伦
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fleet.security.oauth2")
public class FleetOAuth2Properties {

    /**
     * 不需要令牌的路径（Spring PathPattern）。缺省放行健康检查、OpenAPI 文档与错误页；服务按需追加，
     * 写了本项就整体替换缺省值，所以追加时要把缺省项一并写上。
     */
    private List<String> permitPaths = new ArrayList<>(List.of("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/error"));

    /**
     * 访问令牌里业务用户 ID 的声明名，Keycloak 由 github-api 作用域的映射器写入。
     */
    private String userIdClaim = "user_id";

    /**
     * 业务上下文在本地缓存的时长；Redis 里的副本按用户版本号分键，user 服务改动用户时换版本。
     */
    private Duration contextCacheTtl = Duration.ofSeconds(30);

    /**
     * 允许调用本服务的机器客户端（客户端凭证令牌的 {@code azp}），缺省不允许任何机器令牌。
     */
    private List<String> machineClients = new ArrayList<>();

    /**
     * 只接受这些客户端（{@code azp}）的令牌，缺省不限制；ai-agent 设为 github-ai，只收令牌交换换来的令牌。
     */
    private List<String> requiredAzp = new ArrayList<>();

    /**
     * 出站令牌交换：按 Spring Security 客户端注册 ID 配置所请求的受众。
     */
    private Map<String, TokenExchange> tokenExchange = new LinkedHashMap<>();

    /**
     * 出站取令牌（客户端凭证、令牌交换）访问 Keycloak 令牌端点的超时。
     */
    private TokenEndpoint tokenEndpoint = new TokenEndpoint();

    /**
     * 创建带缺省值的配置，由 Spring Boot 按前缀绑定。
     */
    public FleetOAuth2Properties() {
    }

    /**
     * 一个客户端注册的令牌交换设置。
     */
    @Getter
    @Setter
    public static class TokenExchange {

        /**
         * 交换请求的 {@code audience} 参数（RFC 8693），即下游服务的受众。
         */
        private String audience;

        /**
         * 创建空的令牌交换设置，由 Spring Boot 按前缀绑定。
         */
        public TokenExchange() {
        }
    }

    /**
     * 令牌端点的 HTTP 超时。HTTP 客户端库缺省不限读取时间，Keycloak 卡住时取令牌的请求与线程会一直挂着，
     * 所以给出有限的缺省值；令牌端点通常在百毫秒内响应。
     */
    @Getter
    @Setter
    public static class TokenEndpoint {

        /**
         * 建立连接的超时，缺省 5 秒。
         */
        private Duration connectTimeout = Duration.ofSeconds(5);

        /**
         * 等待响应数据的超时，缺省 10 秒。
         */
        private Duration readTimeout = Duration.ofSeconds(10);

        /**
         * 创建带缺省值的超时设置，由 Spring Boot 按前缀绑定。
         */
        public TokenEndpoint() {
        }
    }
}
