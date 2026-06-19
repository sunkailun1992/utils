package com.kellen.config.feign;

import feign.Feign;
import okhttp3.ConnectionPool;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Feign 的 OkHttp 客户端配置。
 *
 * <p>在 Feign 自动配置前注册带连接池与读写/连接超时的 OkHttpClient，供声明式调用复用连接。</p>
 *
 * @author 孙凯伦
 */
@Configuration
@ConditionalOnClass(Feign.class)
@AutoConfigureBefore(FeignAutoConfiguration.class)
public class FeignOkHttpConfig {


    /**
     * 构建 Feign 使用的 OkHttpClient：连接 60s、读 60s、写 120s，启用连接池。
     *
     * @return OkHttpClient 实例
     */
    @Bean
    public okhttp3.OkHttpClient okHttpClient(){
        return new okhttp3.OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool())
                // .addInterceptor();
                .build();
    }
}
