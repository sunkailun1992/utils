package com.kellen.config.web;

import com.kellen.security.config.TenantProperties;
import com.kellen.traffic.CanaryContext;
import com.kellen.traffic.CanaryGrant;
import com.kellen.traffic.TrafficGovernanceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class CanaryRequestTest {

    private static final String KEY = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    private final TrafficGovernanceProperties properties = new TrafficGovernanceProperties();
    private final ReqInterceptor interceptor = new ReqInterceptor(new TenantProperties(), properties);

    @AfterEach
    void clear() throws Exception {
        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), this, null);
    }

    @Test
    void rawHeaderCannotSetCanaryOnStableService() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Canary-Tag", "gray");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), this)).isTrue();
        assertThat(CanaryContext.get()).isNull();
    }

    @Test
    void verifiedGrantIsScopedToServiceAndClearedAfterRequest() throws Exception {
        configureCanary();
        var request = request("report", Instant.now().plusSeconds(30));
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), this)).isTrue();
        assertThat(CanaryContext.get().grant().tag()).isEqualTo("gray");
        interceptor.afterConcurrentHandlingStarted(request, new MockHttpServletResponse(), this);
        assertThat(CanaryContext.get()).isNull();
    }

    @Test
    void canaryRejectsAbsentInvalidExpiredWrongServiceAndDuplicateGrants() throws Exception {
        configureCanary();
        assertForbidden(new MockHttpServletRequest());
        assertForbidden(request("user", Instant.now().plusSeconds(30)));
        assertForbidden(request("report", Instant.now().minusSeconds(1)));
        assertForbidden(request("report", Instant.now().plusSeconds(120)));
        var differentLane = new MockHttpServletRequest();
        differentLane.addHeader(CanaryGrant.HEADER, CanaryGrant.issue("report", "red", Instant.now().plusSeconds(30), KEY));
        assertForbidden(differentLane);
        var invalid = new MockHttpServletRequest();
        invalid.addHeader(CanaryGrant.HEADER, "v1.report.gray.forged");
        assertForbidden(invalid);
        var duplicate = request("report", Instant.now().plusSeconds(30));
        duplicate.addHeader(CanaryGrant.HEADER, "duplicate");
        assertForbidden(duplicate);
    }

    private void configureCanary() {
        var config = properties.getCanary();
        config.setEnabled(true);
        config.setRequireGrant(true);
        config.setSigningKey(KEY);
        config.setService("report");
        config.setHttpTag("gray");
    }

    private MockHttpServletRequest request(String service, Instant expiry) {
        var request = new MockHttpServletRequest();
        request.addHeader(CanaryGrant.HEADER, CanaryGrant.issue(service, "gray", expiry, KEY));
        return request;
    }

    private void assertForbidden(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, this)).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(CanaryContext.get()).isNull();
    }
}
