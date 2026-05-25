package com.kellen.bean;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.kellen.utils.DynamicSourceTtl;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName FeignConfiguration
 * @Description rpc配置
 * @Author 孙凯伦
 * @Email 376253703@qq.com
 * @Time 2021/6/29 1:36 下午
 */
@Slf4j
@Configuration
public class FeignConfiguration implements RequestInterceptor {


    /**
     * @param template
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: apply
     * @description: TODO  rpc请求投配置
     * @return: void
     * @date: 2021/6/29 1:39 下午
     */
    @Override
    public void apply(RequestTemplate template) {
        //获取当前线程环境
        String dataSource = DynamicSourceTtl.get();
        log.debug("RPC请求地址：{}，RPC环境参数：{}，当前数据库环境：{}", template.url(), dataSource, DynamicDataSourceContextHolder.peek());
        // 对消息头进行配置
        if (StringUtils.isNotBlank(dataSource)) {
            template.header("dataSource", dataSource);
        }
        //事物id
        String currentXid = RootContext.getXID();
        if (!StringUtils.isEmpty(currentXid)) {
            template.header(RootContext.KEY_XID, currentXid);
        }
    }

}
