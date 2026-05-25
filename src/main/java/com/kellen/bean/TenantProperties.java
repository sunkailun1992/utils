package com.kellen.bean;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
     * HTTP/RPC 请求头里读取租户 ID 的 key。
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
