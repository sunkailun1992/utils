package com.kellen.bean;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.kellen.utils.TenantContextHolder;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2020/1/3  11:07 上午
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private final TenantProperties tenantProperties;

    public MyMetaObjectHandler(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        //第一个对应实体属性名, 第二个参数需要填充的值
        setFieldValByName("createDateTime", LocalDateTime.now(), metaObject);
        //第一个对应实体属性名, 第二个参数需要填充的值
        setFieldValByName("modifyDateTime", LocalDateTime.now(), metaObject);
        fillTenantId(metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        //第一个对应实体属性名, 第二个参数需要填充的值
        setFieldValByName("modifyDateTime", LocalDateTime.now(), metaObject);
    }

    private void fillTenantId(MetaObject metaObject) {
        String tenantId = TenantContextHolder.getTenantId();
        String tenantField = tenantProperties.getField();
        if (!tenantProperties.isEnabled() || StringUtils.isBlank(tenantId) || StringUtils.isBlank(tenantField)) {
            return;
        }
        if (metaObject.hasSetter(tenantField) && getFieldValByName(tenantField, metaObject) == null) {
            setFieldValByName(tenantField, tenantId, metaObject);
        }
    }
}
