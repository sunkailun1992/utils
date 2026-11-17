package com.kellen.config.mybatis;

import com.kellen.entity.TenantEntityBase;
import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 自动填充：插入填时间、创建人、修改人、租户与创建部门，更新刷新修改时间与修改人；没有登录用户时不填人和部门，已有值不覆盖。 */
class MyMetaObjectHandlerTest {

    private static final LocalDateTime EARLIER = LocalDateTime.of(2026, 1, 1, 8, 0);

    private final MyMetaObjectHandler handler = new MyMetaObjectHandler(enabledTenants());

    @AfterEach
    void clearContext() {
        UserContextHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void insertFillsAuditFieldsAndTenantFromContext() {
        login("u-1");
        TenantContextHolder.setTenantId("t-1");
        Fixture entity = new Fixture();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateTime()).isNotNull().isEqualTo(entity.getUpdateTime());
        assertThat(entity.getCreateBy()).isEqualTo("u-1");
        assertThat(entity.getUpdateBy()).isEqualTo("u-1");
        assertThat(entity.getTenantId()).isEqualTo("t-1");
        assertThat(entity.getCreateDept()).isEqualTo("d-1");
    }

    @Test
    void insertWithoutLoginUserLeavesCreatorNull() {
        Fixture entity = new Fixture();

        assertThatCode(() -> handler.insertFill(SystemMetaObject.forObject(entity))).doesNotThrowAnyException();

        assertThat(entity.getCreateBy()).isNull();
        assertThat(entity.getUpdateBy()).isNull();
        assertThat(entity.getCreateDept()).isNull();
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void insertKeepsExplicitCreator() {
        login("u-1");
        Fixture entity = new Fixture();
        entity.setCreateBy("seed");
        entity.setCreateDept("seed-dept");

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreateBy()).isEqualTo("seed");
        assertThat(entity.getCreateDept()).isEqualTo("seed-dept");
    }

    @Test
    void updateRefreshesUpdateTimeAndUpdater() {
        login("u-2");
        Fixture entity = new Fixture();
        entity.setCreateTime(EARLIER);
        entity.setUpdateTime(EARLIER);
        entity.setCreateBy("u-0");
        entity.setUpdateBy("u-0");
        entity.setCreateDept("d-0");

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateTime()).isAfter(EARLIER);
        assertThat(entity.getUpdateBy()).isEqualTo("u-2");
        assertThat(entity.getCreateTime()).isEqualTo(EARLIER);
        assertThat(entity.getCreateBy()).isEqualTo("u-0");
        assertThat(entity.getCreateDept()).isEqualTo("d-0");
    }

    private static void login(String userId) {
        UserContextHolder.set(new SecurityUser(userId, "name", "t-1", "d-1", "ALL", List.of(), List.of()));
    }

    private static TenantProperties enabledTenants() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        return properties;
    }

    static final class Fixture extends TenantEntityBase { }
}
