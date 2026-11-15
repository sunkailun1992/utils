package com.kellen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体基类，只含审计与逻辑删除字段，对照芋道 {@code BaseDO}。
 *
 * <p>主键、乐观锁版本号、编码、说明、排序等由用到的实体自己声明；有租户的实体继承 {@link TenantEntityBase}。
 * 审计字段由 {@code MyMetaObjectHandler} 自动填充。</p>
 *
 * @author 孙凯伦
 */
@Getter
@Setter
public abstract class EntityBase implements Serializable {

    /**
     * 创建时间，插入时自动填充。
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 最后更新时间，插入和更新时自动填充。
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 创建人用户ID，插入时取当前登录用户；没有登录用户（种子、后台任务）时为空。
     */
    @Schema(description = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private String creator;

    /**
     * 最后更新人用户ID，插入和更新时取当前登录用户；没有登录用户时不改。
     */
    @Schema(description = "更新人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updater;

    /**
     * 逻辑删除标记，对应 {@code is_deleted} 列。
     */
    @Schema(description = "删除状态（false：未删除，true：删除）")
    @TableLogic(value = "false", delval = "true")
    @TableField("is_deleted")
    private Boolean deleted;
}
