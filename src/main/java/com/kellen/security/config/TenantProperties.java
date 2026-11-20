package com.kellen.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 多租户配置属性。
 *
 * @author 孙凯伦
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /**
     * 默认关闭，避免老服务升级 utils 后立刻影响所有 SQL。
     */
    private boolean enabled = false;

    /**
     * 数据库租户字段名。
     */
    private String column = "tenant_id";

    /**
     * Java 实体租户属性名，用于插入时自动填充。
     */
    private String field = "tenantId";

    /**
     * Dubbo 调用附件里读取与透传租户 ID 的键（HTTP 请求的租户只取认证上下文，不读这些头）。
     */
    private List<String> headerNames = new ArrayList<>(Arrays.asList("tenantId", "tenant-id", "X-Tenant-Id"));

    /**
     * 不需要拼接租户条件的公共表。
     */
    private List<String> ignoreTables = new ArrayList<>();

    /**
     * 没有租户上下文时是否忽略租户条件。开发期可设为 true，生产建议 false。
     */
    private boolean ignoreWithoutTenant = true;
}
