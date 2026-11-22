package com.kellen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 实体基类两层：EntityBase 保留全部公共字段（审计字段按主流项目多数命名：createBy/createTime/updateBy/updateTime），TenantEntityBase 加租户与创建部门。 */
class EntityBaseTest {

    @Test
    void entityBaseKeepsTheCommonFieldsWithMainstreamAuditNames() throws Exception {
        assertThat(Modifier.isAbstract(EntityBase.class.getModifiers())).isTrue();
        List<Field> fields = instanceFields(EntityBase.class);
        assertThat(fields).extracting(Field::getName).containsExactly("id", "code", "description", "createTime", "createBy",
                "updateTime", "updateBy", "deleted", "label", "sort", "version");
        List<Class<?>> types = fields.stream().<Class<?>>map(Field::getType).toList();
        assertThat(types).containsExactly(String.class, String.class, String.class, LocalDateTime.class, String.class,
                LocalDateTime.class, String.class, Boolean.class, String.class, Integer.class, Integer.class);
        TableId id = EntityBase.class.getDeclaredField("id").getAnnotation(TableId.class);
        assertThat(id.value()).isEqualTo("id");
        assertThat(id.type()).isEqualTo(IdType.ASSIGN_ID);
        assertThat(EntityBase.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        Field deleted = EntityBase.class.getDeclaredField("deleted");
        assertThat(deleted.getAnnotation(TableLogic.class).value()).isEqualTo("false");
        assertThat(deleted.getAnnotation(TableLogic.class).delval()).isEqualTo("true");
        assertThat(deleted.getAnnotation(TableField.class).value()).isEqualTo("is_deleted");
    }

    @Test
    void tenantEntityBaseAddsTenantAndCreateDept() throws Exception {
        assertThat(Modifier.isAbstract(TenantEntityBase.class.getModifiers())).isTrue();
        assertThat(TenantEntityBase.class.getSuperclass()).isEqualTo(EntityBase.class);
        assertThat(instanceFields(TenantEntityBase.class)).extracting(Field::getName).containsExactly("tenantId", "createDept");
        TableField tenant = TenantEntityBase.class.getDeclaredField("tenantId").getAnnotation(TableField.class);
        assertThat(tenant.value()).isEqualTo("tenant_id");
        assertThat(tenant.fill()).isEqualTo(FieldFill.INSERT);
        TableField dept = TenantEntityBase.class.getDeclaredField("createDept").getAnnotation(TableField.class);
        assertThat(dept.value()).isEqualTo("create_dept");
        assertThat(dept.fill()).isEqualTo(FieldFill.INSERT);
    }

    private static List<Field> instanceFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()) && !field.isSynthetic())
                .toList();
    }
}
