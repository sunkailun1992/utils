package com.kellen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 实体基类按芋道两层：EntityBase 只有审计与逻辑删除字段，TenantEntityBase 只多租户ID。 */
class EntityBaseTest {

    @Test
    void entityBaseDeclaresOnlyAuditFields() throws Exception {
        assertThat(Modifier.isAbstract(EntityBase.class.getModifiers())).isTrue();
        List<Field> fields = instanceFields(EntityBase.class);
        assertThat(fields).extracting(Field::getName)
                .containsExactly("createTime", "updateTime", "creator", "updater", "deleted");
        List<Class<?>> types = fields.stream().<Class<?>>map(Field::getType).toList();
        assertThat(types).containsExactly(LocalDateTime.class, LocalDateTime.class, String.class, String.class, Boolean.class);
        Field deleted = EntityBase.class.getDeclaredField("deleted");
        assertThat(deleted.getAnnotation(TableLogic.class).value()).isEqualTo("false");
        assertThat(deleted.getAnnotation(TableLogic.class).delval()).isEqualTo("true");
        assertThat(deleted.getAnnotation(TableField.class).value()).isEqualTo("is_deleted");
    }

    @Test
    void tenantEntityBaseAddsOnlyTenantId() throws Exception {
        assertThat(Modifier.isAbstract(TenantEntityBase.class.getModifiers())).isTrue();
        assertThat(TenantEntityBase.class.getSuperclass()).isEqualTo(EntityBase.class);
        assertThat(instanceFields(TenantEntityBase.class)).extracting(Field::getName).containsExactly("tenantId");
        TableField tenant = TenantEntityBase.class.getDeclaredField("tenantId").getAnnotation(TableField.class);
        assertThat(tenant.value()).isEqualTo("tenant_id");
        assertThat(tenant.fill()).isEqualTo(FieldFill.INSERT);
    }

    private static List<Field> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                .toList();
    }
}
