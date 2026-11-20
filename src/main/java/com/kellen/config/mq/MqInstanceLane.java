package com.kellen.config.mq;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 本实例的泳道与灰度标签，取 Nacos 注册元数据 {@code traffic.lane}、{@code canary.tag} 的生效值，
 * 与服务发现、网关灰度路由看到的是同一份。
 *
 * <p>值会写进 SQL92 订阅表达式与消费组名，所以只接受字母、数字与 {@code . _ : -}，最长 64 位；
 * 不合法时启动失败。</p>
 *
 * @param lane      泳道，缺省 {@code stable}
 * @param canaryTag 灰度标签，没有时为 {@code null}
 */
public record MqInstanceLane(String lane, String canaryTag) {

    /** 稳定泳道。 */
    public static final String STABLE = "stable";

    /** 泳道的元数据键。 */
    public static final String LANE_KEY = "traffic.lane";

    /** 灰度标签的元数据键。 */
    public static final String TAG_KEY = "canary.tag";

    /** Nacos 注册元数据所在的属性前缀。 */
    private static final String METADATA = "spring.cloud.nacos.discovery.metadata";

    /** 与流量治理配置 {@code allowed-value-pattern} 一致的合法取值。 */
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._:-]{1,64}$");

    /**
     * 规整空值并校验取值。
     *
     * @param lane      泳道
     * @param canaryTag 灰度标签
     */
    public MqInstanceLane {
        lane = lane == null || lane.isBlank() ? STABLE : lane.trim();
        canaryTag = canaryTag == null || canaryTag.isBlank() ? null : canaryTag.trim();
        check(LANE_KEY, lane);
        if (canaryTag != null) {
            check(TAG_KEY, canaryTag);
        }
    }

    /**
     * 从环境里读本实例的注册元数据。
     *
     * @param environment Spring 环境
     * @return 本实例的泳道与灰度标签
     */
    public static MqInstanceLane resolve(Environment environment) {
        Map<String, String> metadata = Binder.get(environment)
                .bind(METADATA, Bindable.mapOf(String.class, String.class))
                .orElseGet(Map::of);
        return new MqInstanceLane(metadata.get(LANE_KEY), metadata.get(TAG_KEY));
    }

    /**
     * 是否稳定泳道。
     *
     * @return 稳定泳道时为 true
     */
    public boolean stable() {
        return STABLE.equals(lane);
    }

    private static void check(String key, String value) {
        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalStateException("Nacos 注册元数据 " + key + " 不合法：只允许字母、数字与 . _ : -，最长 64 位");
        }
    }
}
