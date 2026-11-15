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

/** 自动填充：插入填时间、创建人、修改人与租户，更新刷新修改时间与修改人；没有登录用户时不填人，已有创建人不覆盖。 */
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
        assertThat(entity.getCreator()).isEqualTo("u-1");
        assertThat(entity.getUpdater()).isEqualTo("u-1");
        assertThat(entity.getTenantId()).isEqualTo("t-1");
    }

    @Test
    void insertWithoutLoginUserLeavesCreatorNull() {
        Fixture entity = new Fixture();

        assertThatCode(() -> handler.insertFill(SystemMetaObject.forObject(entity))).doesNotThrowAnyException();

        assertThat(entity.getCreator()).isNull();
        assertThat(entity.getUpdater()).isNull();
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getUpdateTime()).isNotNull();
    }

    @Test
    void insertKeepsExplicitCreator() {
        login("u-1");
        Fixture entity = new Fixture();
        entity.setCreator("seed");

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getCreator()).isEqualTo("seed");
    }

    @Test
    void updateRefreshesUpdateTimeAndUpdater() {
        login("u-2");
        Fixture entity = new Fixture();
        entity.setCreateTime(EARLIER);
        entity.setUpdateTime(EARLIER);
        entity.setCreator("u-0");
        entity.setUpdater("u-0");

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateTime()).isAfter(EARLIER);
        assertThat(entity.getUpdater()).isEqualTo("u-2");
        assertThat(entity.getCreateTime()).isEqualTo(EARLIER);
        assertThat(entity.getCreator()).isEqualTo("u-0");
    }

    private static void login(String userId) {
        UserContextHolder.set(new SecurityUser(userId, "name", "t-1", List.of()));
    }

    private static TenantProperties enabledTenants() {
        TenantProperties properties = new TenantProperties();
        properties.setEnabled(true);
        return properties;
    }

    static final class Fixture extends TenantEntityBase { }
}
