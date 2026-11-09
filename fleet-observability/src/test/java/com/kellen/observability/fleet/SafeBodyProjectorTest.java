package com.kellen.observability.fleet;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

/** 合成允许值与秘密同时出现，验证投影不是仅删除已知敏感字段的黑名单。 */
class SafeBodyProjectorTest {
    private final SafeBodyProjector projector = new SafeBodyProjector(new FleetObservabilityProperties());

    @Test void retrievalKeepsTypedControlsButNoQueryOrUnknownData() {
        var result = project("POST", "/api/rag/retrievals", SafeBodyProjector.Direction.REQUEST,
                "{\"topK\":3,\"similarityThreshold\":0.5,\"collectionName\":\"fixture_docs\",\"query\":\"SYNTHETIC_SECRET\",\"unknown\":\"SYNTHETIC_SECRET\"}");
        assertThat(result.body()).contains("\"topK\":3", "fixture_docs").doesNotContain("SECRET", "query", "unknown");
        assertThat(project("POST", "/api/rag/retrievals", SafeBodyProjector.Direction.REQUEST,
                "{\"topK\":\"SYNTHETIC_SECRET\",\"similarityThreshold\":2,\"collectionName\":\"bad value\"}").body()).isNull();
    }

    @Test void envelopeAndResultCountsHaveNoFreeText() {
        var result = project("POST", "/api/rag/retrievals", SafeBodyProjector.Direction.RESPONSE,
                "{\"code\":\"0\",\"success\":true,\"message\":\"SYNTHETIC_SECRET\",\"data\":{\"enabled\":true,\"matched\":true,\"sourceCount\":3,\"context\":\"SYNTHETIC_SECRET\"}}");
        assertThat(result.body()).contains("sourceCount", "enabled", "\"success\":true").doesNotContain("SECRET", "context", "message");
    }

    @Test void aiHttpRoutesNeverCaptureContentEvenWhenDiagnosticEnabled() {
        var properties = new FleetObservabilityProperties();
        properties.setAiContentEnabled(true);
        assertThat(new SafeBodyProjector(properties).projectHttp("POST", "/api/ai/chat", SafeBodyProjector.Direction.REQUEST,
                "{\"page\":1,\"content\":\"SYNTHETIC_SECRET\"}".getBytes(StandardCharsets.UTF_8), "application/json", true).omitted()).isEqualTo("policy");
    }

    @Test void invalidDuplicateDeepAndOversizeJsonAreOmittedEntirely() {
        assertThat(project("POST", "/fixture", SafeBodyProjector.Direction.REQUEST, "{\"page\":1,\"page\":2}").omitted()).isEqualTo("invalid_json");
        assertThat(project("POST", "/fixture", SafeBodyProjector.Direction.REQUEST, "{\"page\":1}{\"page\":2}").omitted()).isEqualTo("invalid_json");
        assertThat(project("POST", "/fixture", SafeBodyProjector.Direction.REQUEST, "[".repeat(40) + "0" + "]".repeat(40)).omitted()).isEqualTo("invalid_json");
        assertThat(project("POST", "/fixture", SafeBodyProjector.Direction.REQUEST, "{\"text\":\"" + "x".repeat(8200) + "\"}").omitted()).isEqualTo("too_large");
        assertThat(projector.projectHttp("POST", "/fixture", SafeBodyProjector.Direction.REQUEST,
                new byte[]{(byte) 0xc3, 0x28}, "application/json", true).omitted()).isEqualTo("invalid_encoding");
    }

    @Test void unsupportedMediaAndIncompleteInputAreNeverParsed() {
        byte[] bytes = "{\"page\":1}".getBytes(StandardCharsets.UTF_8);
        assertThat(projector.projectHttp("POST", "/fixture", SafeBodyProjector.Direction.REQUEST, bytes, "application/json", false).omitted()).isEqualTo("incomplete");
        assertThat(projector.projectHttp("POST", "/fixture", SafeBodyProjector.Direction.RESPONSE, bytes, "text/event-stream", true).omitted()).isEqualTo("stream");
        assertThat(projector.projectHttp("POST", "/fixture", SafeBodyProjector.Direction.REQUEST, bytes, "multipart/form-data", true).omitted()).isEqualTo("multipart");
    }

    @Test void diagnosticContentRequiresServerSwitchAndStrictProjection() {
        String json = "{\"role\":\"user\",\"messageCount\":2,\"content\":\"SYNTHETIC_SECRET\",\"token\":\"SYNTHETIC_SECRET\"}";
        assertThat(projector.projectAiDiagnostic(json)).isEmpty();
        var properties = new FleetObservabilityProperties(); properties.setAiContentEnabled(true);
        var enabled = new SafeBodyProjector(properties);
        assertThat(enabled.projectAiDiagnostic(json).orElseThrow()).contains("user", "messageCount").doesNotContain("SECRET", "content", "token");
        assertThat(enabled.projectAiDiagnostic("SYNTHETIC_SECRET")).isEmpty();
    }
    @Test void errorCodeOnlyComesFromAnAlreadyTypedProjection() {
        assertThat(projector.businessErrorCode(project("GET", "/fixture", SafeBodyProjector.Direction.RESPONSE, "{\"code\":\"A0301\"}"))).contains("A0301");
        assertThat(projector.businessErrorCode(project("GET", "/fixture", SafeBodyProjector.Direction.RESPONSE, "{\"code\":1}"))).contains("1");
        assertThat(projector.businessErrorCode(project("GET", "/fixture", SafeBodyProjector.Direction.RESPONSE, "{\"code\":\"SYNTHETIC_SECRET\"}"))).isEmpty();
    }
    @Test void collectionTokensAndModelIdentifiersCannotCarryUrls() {
        for (String value : new String[]{"https://example.invalid/SYNTHETIC_TOKEN", "folder/SYNTHETIC_TOKEN", "../SYNTHETIC_TOKEN"}) {
            assertThat(project("POST", "/api/rag/retrievals", SafeBodyProjector.Direction.REQUEST,
                    "{\"collectionName\":\"" + value + "\"}").body()).isNull();
        }
        assertThat(project("GET", "/api/agents/model", SafeBodyProjector.Direction.RESPONSE,
                "{\"model\":\"https://example.invalid/SYNTHETIC_TOKEN\"}").body()).isNull();
        assertThat(project("GET", "/api/agents/model", SafeBodyProjector.Direction.RESPONSE,
                "{\"model\":\"namespace/model:latest\"}").body()).contains("namespace/model:latest");
    }

    private SafeBodyProjector.Projection project(String method, String route, SafeBodyProjector.Direction direction, String body) {
        return projector.projectHttp(method, route, direction, body.getBytes(StandardCharsets.UTF_8), "application/json", true);
    }
}
