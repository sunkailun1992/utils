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
 * @ClassName EntityBase
 * @Description 实体基类
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/7/23 11:15 上午
 */
@Getter
@Setter
public class EntityBase implements Serializable {

    @Schema(description = "序列")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @Schema(description = "编码")
    private String code;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateTime;

    @Schema(description = "创建人")
    private String createName;

    @Schema(description = "修改时间")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifyDateTime;

    @Schema(description = "修改人")
    private String modifyName;

    @Schema(description = "删除状态（0：未删除，1：删除）")
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "标签")
    private String label;

    @Schema(description = "排序")
    private Integer sorting;

    @Version
    @Schema(hidden = true, description = "版本号")
    private Integer version;

    @Schema(description = "租户id")
    @TableField(value = "tenant_id", fill = FieldFill.INSERT)
    private String tenantId;
}
