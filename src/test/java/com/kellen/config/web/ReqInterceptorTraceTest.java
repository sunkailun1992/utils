package com.kellen.config.web;

import com.kellen.security.config.TenantProperties;
import com.kellen.traffic.TrafficGovernanceProperties;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

/** 响应联查字段来自当前 OTel span，不接受业务 MDC 或调用方任意 ID。 */
class ReqInterceptorTraceTest {
    @Test void arbitraryBusinessMdcIsNotWrittenAsTraceHeader() throws Exception {
        MDC.put("traceId", "SYNTHETIC_SECRET");
        try {
            var response = new MockHttpServletResponse();
            interceptor().preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(response.getHeader("traceId")).isNull();
        } finally { MDC.remove("traceId"); }
    }
    @Test void validCurrentSpanIsExposedAsTheExistingHeader() throws Exception {
        var span = Span.wrap(SpanContext.create("0123456789abcdef0123456789abcdef", "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault()));
        try (var scope = span.makeCurrent()) {
            var response = new MockHttpServletResponse();
            interceptor().preHandle(new MockHttpServletRequest(), response, new Object());
            assertThat(response.getHeader("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
        }
    }
    private ReqInterceptor interceptor() {
        var tenant = new TenantProperties(); tenant.setEnabled(false);
        return new ReqInterceptor(tenant, new TrafficGovernanceProperties());
    }
}
