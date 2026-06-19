package com.kellen.config.actuator;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 优雅停机端点。
 *
 * <p>暴露 Actuator 写操作端点 {@code shutdownGraceFul}：先从 Nacos 注销实例以停止接收新流量，
 * 等待配置的缓冲时间后再关闭应用，避免在途请求被强制中断。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
@Component
@Endpoint(id = "shutdownGraceFul")
public class ServiceShutDownEndpoint {

    @Resource
    private NacosAutoServiceRegistration serviceRegistration;

    @Resource
    private  ApplicationContext context;

    /** 下线服务后关闭应用前等待的时间(秒) */
    @Value("${stopService.waitTime:120}")
    private int waitTime;



    /**
     * 优雅下线当前服务：注销 Nacos 注册、等待缓冲时间后关闭 Spring 应用。
     *
     * @return 包含下线结果标记的响应
     */
    @WriteOperation
    public Map<String, Object> shutdownGraceFul() {
        log.info("开始服务下线");
        serviceRegistration.stop();
        log.info("完成服务下线");
        log.info("等待{}s, 关闭应用", waitTime);
        try {
            TimeUnit.SECONDS.sleep(waitTime);
        } catch (InterruptedException e) {
            log.info("interrupted!", e);
        }
        log.info("Closing application...");
        SpringApplication.exit(context);
        Map<String, Object> result = new HashMap<>();

        result.put("shutdownGraceFul", true);
        return result;
    }

}