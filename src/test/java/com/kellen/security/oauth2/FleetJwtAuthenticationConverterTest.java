package com.kellen.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 转换器在请求时才取业务上下文提供方：启动检查保证它存在，万一请求时仍取不到，按 user 服务不可用处理（503），
 * 不再当成用户不存在。
 */
class FleetJwtAuthenticationConverterTest {

    @Test
    void providerMissingAtRequestTimeIsServiceUnavailable() {
        ObjectProvider<SecurityContextRpcService> none = new DefaultListableBeanFactory().getBeanProvider(SecurityContextRpcService.class);
        FleetJwtAuthenticationConverter converter = new FleetJwtAuthenticationConverter(new FleetOAuth2Properties(),
                new SecurityUserCache(null, Duration.ZERO), none);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("kc-1").claim("user_id", "u1").build();
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(FleetSecurityContextUnavailableException.class);
    }
}
