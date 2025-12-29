package com.gb.bean;

import mybatis.mate.databind.ISensitiveStrategy;
import mybatis.mate.strategy.SensitiveStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TODO 脱敏配置器
 *
 * @author 孙凯伦
 * @className SensitiveStrategyConfig
 * @time 2022-11-15 09:37
 */
@Configuration
public class SensitiveStrategyConfig {
    /**
     * 注入脱敏策略
     */
    @Bean
    public ISensitiveStrategy sensitiveStrategy() {
        // 自定义 testStrategy 类型脱敏处理
        return new SensitiveStrategy().addStrategy("testStrategy", t -> t + "***test***");
    }
}
