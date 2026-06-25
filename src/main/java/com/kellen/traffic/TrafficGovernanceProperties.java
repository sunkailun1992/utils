package com.kellen.traffic;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 流量治理配置。
 *
 * <p>默认值保证本地开发无需配置即可带稳定版本和稳定泳道；生产灰度比例由 Nacos/Dubbo
 * 治理规则和实例元数据控制，不信任公网客户端提交的权重。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "traffic.governance")
public class TrafficGovernanceProperties {

    /**
     * 是否启用流量治理上下文解析和透传。
     */
    private boolean enabled = true;

    /**
     * 请求侧配置。
     */
    private Request request = new Request();

    /**
     * 请求侧配置。
     */
    @Getter
    @Setter
    public static class Request {

        /**
         * 发布版本请求头名。
         */
        private String releaseVersionHeader = TrafficGovernanceHeaders.RELEASE_VERSION;

        /**
         * 流量泳道请求头名。
         */
        private String laneHeader = TrafficGovernanceHeaders.TRAFFIC_LANE;

        /**
         * 灰度 tag 请求头名。
         */
        private String canaryTagHeader = TrafficGovernanceHeaders.CANARY_TAG;

        /**
         * 灰度权重请求头名。默认只作为受控内部头，公网前端不应发送。
         */
        private String canaryWeightHeader = TrafficGovernanceHeaders.CANARY_WEIGHT;

        /**
         * 缺省发布版本。
         */
        private String defaultReleaseVersion = "1.0.0";

        /**
         * 缺省流量泳道。
         */
        private String defaultLane = "stable";

        /**
         * 请求没有显式 tag 时，是否把发布版本作为 Dubbo tag。
         */
        private boolean tagFallbackToReleaseVersion = false;

        /**
         * 是否允许客户端请求头直接携带权重。
         */
        private boolean allowClientWeightHeader = false;

        /**
         * 治理字段允许字符。避免把异常长值或特殊字符透传到 RPC/注册元数据。
         */
        private String allowedValuePattern = "^[A-Za-z0-9._:-]{1,64}$";
    }
}
