package com.kellen.traffic;

/**
 * 流量治理请求头和 Dubbo attachment 常量。
 *
 * <p>这里的版本号是发布/灰度版本，不是数据库乐观锁 {@code version} 字段。</p>
 */
public final class TrafficGovernanceHeaders {

    /**
     * 请求期望访问的发布版本。
     */
    public static final String RELEASE_VERSION = "X-Release-Version";

    /**
     * 请求所属流量泳道。
     */
    public static final String TRAFFIC_LANE = "X-Traffic-Lane";

    /**
     * 请求显式指定的 Dubbo 灰度 tag。
     */
    public static final String CANARY_TAG = "X-Canary-Tag";

    /**
     * 受控客户端权重头。默认不由公网前端设置，比例发布以 Nacos/Dubbo 治理规则为准。
     */
    public static final String CANARY_WEIGHT = "X-Canary-Weight";

    /**
     * 发布版本 attachment key。
     */
    public static final String RELEASE_VERSION_ATTACHMENT = "release.version";

    /**
     * 流量泳道 attachment key。
     */
    public static final String TRAFFIC_LANE_ATTACHMENT = "traffic.lane";

    /**
     * 灰度权重 attachment key。
     */
    public static final String CANARY_WEIGHT_ATTACHMENT = "traffic.weight";

    /**
     * 工具类不允许实例化。
     */
    private TrafficGovernanceHeaders() {
    }
}
