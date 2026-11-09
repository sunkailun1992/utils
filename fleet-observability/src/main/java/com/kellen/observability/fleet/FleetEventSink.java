package com.kellen.observability.fleet;

import java.util.Map;

/** 只接收本库构造的安全事件；实现失败不会向业务请求传播。 */
@FunctionalInterface
public interface FleetEventSink {
    void emit(Map<String, Object> fields);
}
