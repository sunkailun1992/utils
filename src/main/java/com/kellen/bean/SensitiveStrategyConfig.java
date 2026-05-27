package com.kellen.bean;

import mybatis.mate.databind.ISensitiveStrategy;
import mybatis.mate.strategy.SensitiveStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mate 脱敏策略配置。
 *
 * @author 孙凯伦
 */
@Configuration
public class SensitiveStrategyConfig {

    /**
     * 注册脱敏策略。
     *
     * @return 脱敏策略集合
     */
    @Bean
    public ISensitiveStrategy sensitiveStrategy() {
        return new SensitiveStrategy().addStrategy("testStrategy", t -> t + "***test***"); // 保留原有 testStrategy 策略，避免影响已有字段配置。
    }
}
