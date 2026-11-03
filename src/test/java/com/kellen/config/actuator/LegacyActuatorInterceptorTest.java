package com.kellen.config.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyActuatorInterceptorTest {
    @Test
    @SuppressWarnings("removal")
    void accidentallyRegisteringLegacyInterceptorAlwaysFailsClosed() {
        var request = new MockHttpServletRequest("GET", "/actuator/env");
        request.addHeader("actuatorReq", "any-legacy-or-current-value");
        var response = new MockHttpServletResponse();
        assertThat(new ActuatorInterceptor().preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }
}
