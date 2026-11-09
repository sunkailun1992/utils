package com.kellen.observability.fleet;

import org.slf4j.LoggerFactory;
import java.util.Map;

/** 使用 SLF4J key/value 写入 ECS；固定 message 不包含请求、模型或原始异常。 */
public final class Slf4jFleetEventSink implements FleetEventSink {
    @Override public void emit(Map<String, Object> fields) {
        var builder = LoggerFactory.getLogger("com.kellen.observability.fleet.events").atInfo();
        fields.forEach(builder::addKeyValue);
        builder.log("fleet observation");
    }
}
