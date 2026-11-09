package com.kellen.observability.fleet;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/** 已写部分数据后上层恢复空Mono，不能把上层成功误当完整body。 */
class ReactiveLifecycleTest {
    @Test void recoveredWriteFailureDoesNotTurnTheCapturedPrefixIntoACompleteBody() {
        var events = new ArrayList<Map<String, Object>>(); var properties = new FleetObservabilityProperties();
        var filter = new FleetWebFluxFilter(properties, new SafeBodyProjector(properties), events::add);
        var response = new MockServerHttpResponse(); response.getHeaders().set("Content-Type", "application/json");
        response.setWriteHandler(body -> Flux.from(body).then());
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap("{\"success\":true}".getBytes(StandardCharsets.UTF_8));
        HttpHandler delegate = (request, target) -> target.writeWith(Flux.concat(Mono.just(buffer), Mono.error(new IOException("SYNTHETIC_SECRET"))))
                .onErrorResume(failure -> Mono.empty());
        filter.apply(delegate).handle(MockServerHttpRequest.get("/fixture").build(), response).block();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("response_body_complete", false).doesNotContainKeys("response_body", "response_bytes");
        assertThat(events.get(0).toString()).doesNotContain("SECRET");
    }
}
