package com.gb.bean;

import com.alibaba.cloud.nacos.registry.NacosAutoServiceRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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