package com.kellen.security.oauth2;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 对 NAS 上真实 Keycloak（github-dev 域，Terraform 管理）的端到端测试。
 *
 * <p>只在设置了 {@code FLEET_KC_TEST_ISSUER} 时运行（变量在本机私有的 fleet-kc-test.env 里，由 kc_test_user.py 生成），
 * 用专用测试用户 fleet-live-test；HTTPS 证书由 mkcert 签发，JDK 的信任库已导入其根证书。CI 不设置这些变量，自动跳过。</p>
 */
@EnabledIfEnvironmentVariable(named = "FLEET_KC_TEST_ISSUER", matches = ".+")
class KeycloakLiveTest extends KeycloakEndToEndTestSupport {

    @DynamicPropertySource
    static void live(DynamicPropertyRegistry registry) {
        register(registry, () -> System.getenv("FLEET_KC_TEST_ISSUER"), () -> System.getenv("FLEET_KC_TEST_AI_CLIENT_SECRET"));
    }

    @Override
    protected Account account() {
        return new Account(System.getenv("FLEET_KC_TEST_ISSUER"), System.getenv("FLEET_KC_TEST_USERNAME"),
                System.getenv("FLEET_KC_TEST_PASSWORD"), System.getenv("FLEET_KC_TEST_USER_ID"),
                System.getenv("FLEET_KC_TEST_GATEWAY_CLIENT_SECRET"));
    }
}
