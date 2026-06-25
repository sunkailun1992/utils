package com.kellen.traffic;

import com.alibaba.ttl.TransmittableThreadLocal;
import org.apache.commons.lang3.StringUtils;

/**
 * 当前请求流量治理上下文。
 *
 * <p>该上下文用于 HTTP 入口、异步线程和 Dubbo RPC 之间透传发布版本、泳道和灰度 tag。</p>
 */
public final class TrafficGovernanceContext {

    /**
     * 当前线程流量治理快照。
     */
    private static final TransmittableThreadLocal<Snapshot> CONTEXT = new TransmittableThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private TrafficGovernanceContext() {
    }

    /**
     * 写入当前线程治理上下文。
     *
     * @param snapshot 治理快照
     */
    public static void set(Snapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            clear();
            return;
        }
        CONTEXT.set(snapshot);
    }

    /**
     * 获取当前线程治理上下文。
     *
     * @return 治理快照，未设置时返回 null
     */
    public static Snapshot get() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程治理上下文。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 流量治理快照。
     *
     * @param releaseVersion 请求期望访问的发布版本
     * @param lane           请求所属流量泳道
     * @param canaryTag      请求显式指定的 Dubbo tag
     * @param canaryWeight   请求携带的受控权重值
     */
    public record Snapshot(String releaseVersion, String lane, String canaryTag, Integer canaryWeight) {

        /**
         * 是否没有任何有效治理字段。
         *
         * @return true 表示空快照
         */
        public boolean isEmpty() {
            return StringUtils.isBlank(releaseVersion)
                    && StringUtils.isBlank(lane)
                    && StringUtils.isBlank(canaryTag)
                    && canaryWeight == null;
        }
    }
}
