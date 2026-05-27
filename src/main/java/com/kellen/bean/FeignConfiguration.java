package com.kellen.bean;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.kellen.utils.DynamicSourceTtl;
import com.kellen.utils.TenantContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.seata.core.context.RootContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Feign 请求上下文透传配置。
 *
 * <p>负责透传动态数据源、Seata XID 和租户ID，保证跨服务调用时上下文一致。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
@Configuration
public class FeignConfiguration implements RequestInterceptor {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 构造 Feign 请求拦截器。
     *
     * @param tenantProperties 租户配置属性
     */
    public FeignConfiguration(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供请求头透传使用。
    }

    /**
     * 透传当前请求上下文。
     *
     * @param template Feign请求模板
     */
    @Override
    public void apply(RequestTemplate template) {
        String dataSource = DynamicSourceTtl.get(); // 获取当前线程数据源。
        log.debug("RPC请求地址：{}，RPC环境参数：{}，当前数据库环境：{}", template.url(), dataSource, DynamicDataSourceContextHolder.peek()); // 输出调试日志，便于排查跨服务数据源不一致。
        if (StringUtils.isNotBlank(dataSource)) {
            template.header("dataSource", dataSource); // 透传数据源请求头。
        }
        String currentXid = RootContext.getXID(); // 获取当前 Seata 全局事务ID。
        if (!StringUtils.isEmpty(currentXid)) {
            template.header(RootContext.KEY_XID, currentXid); // 透传 Seata XID，保证下游加入同一全局事务。
        }
        List<String> headerNames = tenantProperties.getHeaderNames(); // 读取租户请求头配置。
        if (tenantProperties.isEnabled() && StringUtils.isNotBlank(TenantContextHolder.getTenantId())
                && headerNames != null && !headerNames.isEmpty()) {
            template.header(headerNames.get(0), TenantContextHolder.getTenantId()); // 多租户开启时透传当前租户ID。
        }
    }

}
