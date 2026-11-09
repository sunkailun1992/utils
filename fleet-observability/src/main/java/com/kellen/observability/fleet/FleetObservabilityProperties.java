package com.kellen.observability.fleet;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 受信服务端观测开关；硬上限不能被配置放大，不接受请求头控制。 */
@ConfigurationProperties("fleet.observability")
public class FleetObservabilityProperties {
    private boolean enabled;
    private int maxBodyBytes = 8192;
    private boolean aiContentEnabled;
    private int maxAiContentCharacters = 2048;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxBodyBytes() { return maxBodyBytes; }
    public void setMaxBodyBytes(int value) {
        if (value < 1 || value > 8192) throw new IllegalArgumentException("invalid observation body limit");
        maxBodyBytes = value;
    }
    public boolean isAiContentEnabled() { return aiContentEnabled; }
    public void setAiContentEnabled(boolean enabled) { aiContentEnabled = enabled; }
    public int getMaxAiContentCharacters() { return maxAiContentCharacters; }
    public void setMaxAiContentCharacters(int value) {
        if (value < 1 || value > 2048) throw new IllegalArgumentException("invalid AI diagnostic limit");
        maxAiContentCharacters = value;
    }
}
