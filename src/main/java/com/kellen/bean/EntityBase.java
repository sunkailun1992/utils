package com.kellen.bean;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MyBatis-Plus 实体基类。
 *
 * @author 孙凯伦
 */
@Getter
@Setter
public class EntityBase implements Serializable {

    /**
     * 主键ID。
     */
    @Schema(description = "序列")
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
     * 创建时间。
     */
    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateTime;

    /**
     * 创建人。
     */
    @Schema(description = "创建人")
    private String createName;

    /**
     * 修改时间。
     */
    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifyDateTime;

    /**
     * 修改人。
     */
    @Schema(description = "修改人")
    private String modifyName;

    /**
     * 逻辑删除标记。
     */
    @Schema(description = "删除状态（0：未删除，1：删除）")
    @TableLogic
    private Boolean isDelete;

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
     * MyBatis-Plus 乐观锁版本号。
     */
    @Version
    @Schema(hidden = true, description = "版本号")
    private Integer version;

    /**
     * 租户ID。
     */
    @Schema(description = "租户id")
    @TableField(value = "tenant_id", fill = FieldFill.INSERT)
    private String tenantId;
}
