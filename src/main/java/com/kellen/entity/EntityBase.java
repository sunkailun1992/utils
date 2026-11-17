package com.kellen.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体基类：主键、编码、说明、审计、逻辑删除、标签、排序与乐观锁版本号。
 *
 * <p>审计字段按主流项目的多数命名（RuoYi-Vue、RuoYi-Vue-Plus、JeecgBoot 的 createBy、createTime、updateBy、updateTime），
 * 由 {@code MyMetaObjectHandler} 自动填充；实体继承 {@link TenantEntityBase}。</p>
 *
 * @author 孙凯伦
 */
@Getter
@Setter
public abstract class EntityBase implements Serializable {

    /**
     * 主键，字符串雪花ID。
     */
    @Schema(description = "主键ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 业务编码。
     */
    @Schema(description = "编码")
    private String code;

    /**
     * 业务说明。
     */
    @Schema(description = "说明")
    private String description;

    /**
     * 创建时间，插入时自动填充。
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 创建人用户ID，插入时取当前登录用户；没有登录用户（种子、后台任务）时为空。
     */
    @Schema(description = "创建人")
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 最后更新时间，插入和更新时自动填充。
     */
    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    /**
     * 最后更新人用户ID，插入和更新时取当前登录用户；没有登录用户时不改。
     */
    @Schema(description = "更新人")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 逻辑删除标记，对应 {@code is_deleted} 列。
     */
    @Schema(description = "删除状态（false：未删除，true：删除）")
    @TableLogic(value = "false", delval = "true")
    @TableField("is_deleted")
    private Boolean deleted;

    /**
     * 展示标签。
     */
    @Schema(description = "标签")
    private String label;

    /**
     * 排序值。
     */
    @Schema(description = "排序")
    private Integer sorting;

    /**
     * MyBatis-Plus 乐观锁版本号，更新时回传查询到的值。
     */
    @Version
    @Schema(description = "版本号（乐观锁，更新时回传查询到的值）")
    private Integer version;
}
