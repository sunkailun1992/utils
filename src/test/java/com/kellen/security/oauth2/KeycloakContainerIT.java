package com.kellen.security.oauth2;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 对容器里真实 Keycloak 26.8.2 的端到端测试（导入 src/test/resources/keycloak/github-dev-test-realm.json）。
 *
 * <p>测试域与 utils/ops/github-dev/keycloak/terraform 一致；github-api 作用域的三个映射器（受众 github-api、github-ai 与 user_id）
 * 直接挂在网关客户端上：导入文件里写 clientScopes 会让 Keycloak 不再建内置作用域，签出的令牌声明与正式域相同。口令与密钥只用于容器。</p>
 *
 * <p>CI 里运行；本机没有 Docker 时跳过（对 NAS 的同样场景见 {@link KeycloakLiveTest}）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class KeycloakContainerIT extends KeycloakEndToEndTestSupport {

    @Container
    static final KeycloakContainer KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.8.2")
            .withRealmImportFile("/keycloak/github-dev-test-realm.json");

    @DynamicPropertySource
    static void container(DynamicPropertyRegistry registry) {
        register(registry, KeycloakContainerIT::issuer, () -> "container-ai-secret");
    }

    static String issuer() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/github-dev";
    }

    @Override
    protected Account account() {
        return new Account(issuer(), "fleet-live-test", "container-test-password-1", "live-test-0001", "container-gateway-secret");
    }
}
