package com.kellen.config.wechat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 公共微信小程序配置绑定。
 *
 * <p>公共配置中心使用顶层 {@code wechat.*} 给多个服务共享微信小程序 appId、
 * appSecret 和登录 token 有效期。业务服务仍可提供服务内配置覆盖公共值。</p>
 *
 * @param appId        公共小程序 appId。
 * @param appSecret    公共小程序 appSecret。
 * @param tokenTtlDays 公共登录 token 有效天数。
 */
@ConfigurationProperties(prefix = "wechat")
public record CommonWechatProperties(
        @DefaultValue("") String appId,
        @DefaultValue("") String appSecret,
        @DefaultValue("30") long tokenTtlDays
) {
}
