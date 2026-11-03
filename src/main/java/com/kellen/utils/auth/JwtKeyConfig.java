package com.kellen.utils.auth;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 将当前服务环境接入兼容的 static JWT API；不提供默认密钥，也不记录配置值。 */
@Component
public final class JwtKeyConfig {
    public JwtKeyConfig(Environment environment) {
        JwtUtils.configure(environment);
    }
}
