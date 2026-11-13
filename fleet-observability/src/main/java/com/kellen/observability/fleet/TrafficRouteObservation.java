package com.kellen.observability.fleet;

import io.opentelemetry.api.trace.Span;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Emits only bounded routing decisions and identifiers from the active trace. */
public final class TrafficRouteObservation {
    static final String EVENT = "traffic.route.decision";
    private static final FleetEventSink SINK = new Slf4jFleetEventSink();
    private static final Map<String, Set<String>> VALUES = Map.of(
            "traffic_route_hop", Set.of("gateway", "http_provider", "dubbo_consumer", "dubbo_provider"),
            "traffic_route_mode", Set.of("stable", "canary"),
            "traffic_route_decision", Set.of("selected", "rejected", "unavailable", "failed"));

    private TrafficRouteObservation() {
    }

    static boolean allowed(String key, String value) {
        return value != null && VALUES.getOrDefault(key, Set.of()).contains(value);
    }

    public static void record(String hop, String mode, String decision) {
        record(Span.current(), hop, mode, decision);
    }

    public static void record(Span span, String hop, String mode, String decision) {
        if (!allowed("traffic_route_hop", hop) || !allowed("traffic_route_mode", mode)
                || !allowed("traffic_route_decision", decision)) {
            return;
        }
        try {
            annotate(span, hop, mode, decision);
            var context = span.getSpanContext();
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("event", EVENT);
            fields.put("summary", "traffic route " + hop + " " + mode + " " + decision);
            fields.put("traffic_route_hop", hop);
            fields.put("traffic_route_mode", mode);
            fields.put("traffic_route_decision", decision);
            if (context.isValid()) {
                fields.put("trace_id", context.getTraceId());
                fields.put("span_id", context.getSpanId());
            }
            SINK.emit(fields);
        } catch (RuntimeException ignored) {
            // Routing and business responses must not depend on telemetry.
        }
    }

    public static void annotate(Span span, String hop, String mode, String decision) {
        if (allowed("traffic_route_hop", hop) && allowed("traffic_route_mode", mode)
                && allowed("traffic_route_decision", decision)) {
            span.setAttribute("traffic.route.hop", hop);
            span.setAttribute("traffic.route.mode", mode);
            span.setAttribute("traffic.route.decision", decision);
        }
    }
}
