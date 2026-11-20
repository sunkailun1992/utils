package com.kellen.utils.mq;

import java.time.Duration;
import java.util.List;

/**
 * 单次发送的可选项。
 *
 * <p>写法：{@code MqSendOptions.none().withTag("created").withKeys(eventId)}。延时与顺序不能同时使用，
 * RocketMQ 没有“顺序加定时”的发送方式。</p>
 *
 * @param tag      RocketMQ 的 tag，拼成目的地 {@code 主题:tag}；为空表示不带 tag
 * @param keys     消息 key，便于在 dashboard 按业务 ID 查消息；多个以空格分隔写入 {@code KEYS}
 * @param delay    延时投递时长，按秒取整，须在 1 秒到 {@link #MAX_DELAY} 之间；为空表示立即投递
 * @param orderKey 顺序键，同一顺序键进同一个队列，消费端要用顺序消费；为空表示普通消息
 */
public record MqSendOptions(String tag, List<String> keys, Duration delay, String orderKey) {

    /** 延时上限：RocketMQ 5 定时消息在 broker 缺省配置下最长 3 天（{@code timerMaxDelaySec}）。 */
    public static final Duration MAX_DELAY = Duration.ofDays(3);

    /**
     * 校验取值并把 key 列表固定下来。
     *
     * @param tag      RocketMQ 的 tag
     * @param keys     消息 key
     * @param delay    延时投递时长
     * @param orderKey 顺序键
     */
    public MqSendOptions {
        keys = keys == null ? List.of() : List.copyOf(keys);
        if (delay != null && (delay.compareTo(Duration.ofSeconds(1)) < 0 || delay.compareTo(MAX_DELAY) > 0)) {
            throw new IllegalArgumentException("延时须在 1 秒到 3 天之间");
        }
        if (delay != null && orderKey != null) {
            throw new IllegalArgumentException("延时消息与顺序消息不能同时使用");
        }
    }

    /**
     * 不带任何选项：立即投递的普通消息。
     *
     * @return 空选项
     */
    public static MqSendOptions none() {
        return new MqSendOptions(null, List.of(), null, null);
    }

    /**
     * 带上 tag。
     *
     * @param tag RocketMQ 的 tag
     * @return 新的选项
     */
    public MqSendOptions withTag(String tag) {
        return new MqSendOptions(tag, keys, delay, orderKey);
    }

    /**
     * 带上消息 key，通常写事件ID或业务单号。
     *
     * @param keys 消息 key
     * @return 新的选项
     */
    public MqSendOptions withKeys(String... keys) {
        return new MqSendOptions(tag, List.of(keys), delay, orderKey);
    }

    /**
     * 延时投递。
     *
     * @param delay 延时时长，1 秒到 3 天
     * @return 新的选项
     */
    public MqSendOptions withDelay(Duration delay) {
        return new MqSendOptions(tag, keys, delay, orderKey);
    }

    /**
     * 按顺序键发送顺序消息。
     *
     * @param orderKey 顺序键
     * @return 新的选项
     */
    public MqSendOptions withOrderKey(String orderKey) {
        return new MqSendOptions(tag, keys, delay, orderKey);
    }
}
