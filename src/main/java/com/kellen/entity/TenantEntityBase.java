package com.kellen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 带租户的实体基类，在 {@link EntityBase} 的审计字段之外只多租户ID，对照芋道 {@code TenantBaseDO}。
 *
 * @author 孙凯伦
 */
@Getter
@Setter
public abstract class TenantEntityBase extends EntityBase {

    /**
     * 租户ID，插入时按当前租户上下文自动填充。
     */
    @Schema(description = "租户ID")
    @TableField(value = "tenant_id", fill = FieldFill.INSERT)
    private String tenantId;
}
