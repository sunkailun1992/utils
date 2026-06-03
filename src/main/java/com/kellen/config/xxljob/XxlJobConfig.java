package com.kellen.config.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB执行器配置
 *
 * @author sunkailun
 * @DateTime 2026/6/3 11:40
 * @email 376253703@qq.com
 */
@Configuration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@EnableConfigurationProperties(XxlJobProperties.class)
@ConditionalOnProperty(prefix = "xxl.job.admin", name = "addresses")
public class XxlJobConfig {

    /**
     * 初始化XXL-JOB执行器
     *
     * @param properties: XXL-JOB执行器配置
     * @return XxlJobSpringExecutor: XXL-JOB Spring执行器
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    @Bean
    @ConditionalOnProperty(prefix = "xxl.job", name = "enabled", havingValue = "true", matchIfMissing = true)
    public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
        // 创建XXL-JOB Spring执行器实例。
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        // 设置调度中心地址，执行器通过该地址向Admin注册和发送心跳。
        executor.setAdminAddresses(properties.getAdmin().getAddresses());
        // 设置执行器AppName，必须与XXL-JOB后台执行器管理中的AppName一致。
        executor.setAppname(properties.getExecutor().getAppname());
        // 设置执行器注册地址，通常为空并由ip和port自动组合。
        executor.setAddress(properties.getExecutor().getAddress());
        // 设置执行器对调度中心暴露的IP，跨网段时应配置为蒲公英等虚拟网络IP。
        executor.setIp(properties.getExecutor().getIp());
        // 设置执行器对调度中心暴露的端口，调度中心需要能访问该端口。
        executor.setPort(properties.getExecutor().getPort());
        // 设置调度中心访问令牌，必须与XXL-JOB Admin配置保持一致。
        executor.setAccessToken(properties.getAccessToken());
        // 设置任务执行日志目录，避免日志散落到不可控路径。
        executor.setLogPath(properties.getExecutor().getLogpath());
        // 设置任务执行日志保留天数，避免本地磁盘无限增长。
        executor.setLogRetentionDays(properties.getExecutor().getLogretentiondays());
        // 返回执行器Bean，Spring销毁时会调用destroy释放端口和线程。
        return executor;
    }
}
