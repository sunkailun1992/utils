package com.kellen.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充处理器。
 *
 * @author 孙凯伦
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 构造公共字段填充处理器。
     *
     * @param tenantProperties 租户配置属性
     */
    public MyMetaObjectHandler(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供插入时自动填充租户字段。
    }

    /**
     * 插入时填充公共字段。
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        setFieldValByName("createDateTime", LocalDateTime.now(), metaObject); // 新增时填充创建时间。
        setFieldValByName("modifyDateTime", LocalDateTime.now(), metaObject); // 新增时同步填充修改时间。
        fillTenantId(metaObject); // 新增时按当前租户上下文填充租户ID。
    }

    /**
     * 更新时填充公共字段。
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("modifyDateTime", LocalDateTime.now(), metaObject); // 更新时刷新修改时间。
    }

    /**
     * 填充租户ID。
     *
     * @param metaObject MyBatis 元对象
     */
    private void fillTenantId(MetaObject metaObject) {
        String tenantId = TenantContextHolder.getTenantId(); // 从租户上下文读取租户ID。
        String tenantField = tenantProperties.getField(); // 读取实体租户属性名。
        if (!tenantProperties.isEnabled() || StringUtils.isBlank(tenantId) || StringUtils.isBlank(tenantField)) {
            return; // 未开启租户、缺少租户ID或缺少字段名时不填充。
        }
        if (metaObject.hasSetter(tenantField) && getFieldValByName(tenantField, metaObject) == null) {
            setFieldValByName(tenantField, tenantId, metaObject); // 仅在实体存在 setter 且字段为空时写入租户ID。
        }
    }
}
