package com.kellen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 带租户的实体基类：在 {@link EntityBase} 之外加租户ID与创建部门（创建部门对照 RuoYi-Vue-Plus 的 {@code createDept}）。
 *
 * <p>创建部门记录这行数据创建时登录用户所在的部门，供按部门的数据权限过滤使用；要按部门过滤的表在数据权限规则里
 * 指向 {@code create_dept}。实体自己的业务部门字段（例如用户所属部门 {@code dept_id}）与它含义不同，互不影响。</p>
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

    /**
     * 创建部门ID，插入时取当前登录用户的部门；没有登录用户或用户没有部门时为空。
     */
    @Schema(description = "创建部门ID")
    @TableField(value = "create_dept", fill = FieldFill.INSERT)
    private String createDept;
}
