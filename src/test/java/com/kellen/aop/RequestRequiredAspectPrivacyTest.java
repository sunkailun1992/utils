package com.kellen.aop;

import com.kellen.log.entity.ElasticSearchRequestLog;
import com.kellen.log.service.ElasticSearchRequestLogService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 旧审计入口不能绕过传输层策略，把请求、返回值或扩展点原文重新写入 ES。 */
class RequestRequiredAspectPrivacyTest {
    @Test void legacyAuditStoresOnlyMetadataWithoutSerializingBusinessObjects() {
        var service = mock(ElasticSearchRequestLogService.class);
        var aspect = new RequestRequiredAspect();
        ReflectionTestUtils.setField(aspect, "elasticSearchRequestLogService", service);
        var request = new MockHttpServletRequest("POST", "/fixture/SYNTHETIC_SECRET");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/fixture/{id}");
        Object forbiddenSerialization = new Object() { @Override public String toString() { throw new AssertionError("business object must not be serialized"); } };
        ReflectionTestUtils.invokeMethod(aspect, "setLog", null, request, 1L,
                Map.of("password", "SYNTHETIC_SECRET"), forbiddenSerialization, "SYNTHETIC_SECRET", "SYNTHETIC_SECRET");
        var entry = ArgumentCaptor.forClass(ElasticSearchRequestLog.class);
        verify(service).insert(entry.capture());
        assertThat(entry.getValue().getUrl()).isEqualTo("/fixture/{id}");
        assertThat(entry.getValue().getRequest()).isEqualTo("{\"omitted\":\"policy\"}");
        assertThat(entry.getValue().getResults()).isEqualTo("{\"omitted\":\"policy\"}");
        assertThat(entry.getValue().getUsername()).isNull();
        assertThat(entry.getValue().getDescription()).isNull();
        assertThat(entry.getValue().getIp()).isNull();
    }
}
