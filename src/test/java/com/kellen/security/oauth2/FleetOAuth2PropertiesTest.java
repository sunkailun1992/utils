package com.kellen.security.oauth2;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出站令牌端点的缺省超时：Keycloak 卡住时取令牌的请求要在有限时间内失败，不能无限等待。
 */
class FleetOAuth2PropertiesTest {

    @Test
    void tokenEndpointTimeoutsDefaultToFiveAndTenSeconds() {
        FleetOAuth2Properties.TokenEndpoint endpoint = new FleetOAuth2Properties().getTokenEndpoint();
        assertThat(endpoint.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(endpoint.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
    }
}
