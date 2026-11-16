package com.kellen.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
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
     * 插入时填充创建时间、更新时间、创建人、更新人、租户ID与创建部门；实体上已有的值不覆盖。
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(); // 同一次插入的创建时间与更新时间取同一时刻。
        String userId = currentUserId(); // 没有登录用户（种子、后台任务）时为 null，不填创建人与更新人。
        fillIfAbsent("createTime", now, metaObject); // 新增时填充创建时间。
        fillIfAbsent("updateTime", now, metaObject); // 新增时同步填充更新时间。
        fillIfAbsent("creator", userId, metaObject); // 新增时填充创建人，已有值（如种子指定）不覆盖。
        fillIfAbsent("updater", userId, metaObject); // 新增时同步填充更新人。
        fillTenantId(metaObject); // 新增时按当前租户上下文填充租户ID。
        fillIfAbsent("createDept", currentDeptId(), metaObject); // 新增时记录创建部门，供按部门的数据权限过滤。
    }

    /**
     * 更新时刷新更新时间，有登录用户时把更新人改为当前用户。
     *
     * @param metaObject MyBatis 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject); // 更新时刷新更新时间。
        setFieldValByName("updater", currentUserId(), metaObject); // 值为 null 时框架不写入，没有登录用户就保留原更新人。
    }

    /**
     * 读取当前登录用户ID，空白视为没有登录用户。
     *
     * @return 用户ID，没有登录用户时为 null
     */
    private static String currentUserId() {
        return StringUtils.trimToNull(UserContextHolder.getUserId()); // 空白用户ID不写入审计字段。
    }

    /**
     * 读取当前登录用户的部门ID，没有登录用户或没有部门时为空。
     *
     * @return 部门ID，没有时为 null
     */
    private static String currentDeptId() {
        SecurityUser user = UserContextHolder.get(); // 读取认证过滤器写入的当前用户快照。
        return user == null ? null : StringUtils.trimToNull(user.getDeptId()); // 空白部门ID不写入。
    }

    /**
     * 字段为空时填充值。
     *
     * @param fieldName 实体属性名
     * @param value 要填充的值，为 null 时不填
     * @param metaObject MyBatis 元对象
     */
    private void fillIfAbsent(String fieldName, Object value, MetaObject metaObject) {
        if (getFieldValByName(fieldName, metaObject) == null) {
            setFieldValByName(fieldName, value, metaObject); // 实体没有该属性或值为 null 时框架自动跳过。
        }
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
