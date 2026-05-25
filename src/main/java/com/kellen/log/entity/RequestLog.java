package com.kellen.log.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Created with IntelliJ IDEA.
 *
 * @author sunkailun
 * @DateTime 2018/7/16  上午10:10
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Data
@Schema(description = "日志实体类")
public class RequestLog implements Serializable {
    @Id
    @Schema(name = "_id", description = "序列")
    @SuppressWarnings("all")
    private ObjectId _id;

    @Schema(name = "userId", description = "用户id")
    private Long userId;

    @Schema(name = "username", description = "账号")
    private String username;

    @Schema(name = "name", description = "用户名称")
    private String name;

    @Schema(name = "url", description = "请求地址")
    private String url;

    @Schema(name = "elapsedTime", description = "消耗时间")
    private Long elapsedTime;

    @Schema(name = "request", description = "请求参数")
    private Object request;

    @Schema(name = "results", description = "返回结果")
    private Object results;

    @Schema(name = "interfaceName", description = "接口名称")
    private String interfaceName;

    @Schema(name = "performBefore", description = "执行前")
    private String performBefore;

    @Schema(name = "performAfter", description = "执行后")
    private String performAfter;

    @Schema(name = "system", description = "系统名称")
    private String systemName;

    @Schema(name = "ip", description = "ip地址")
    private String ip;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "环境")
    private String environment;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateTime;

    @Schema(description = "创建人")
    private String createName;

    @Schema(description = "修改时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifyDateTime;

    @Schema(description = "修改人")
    private String modifyName;

    @Schema(description = "删除状态（0：未删除，1：删除）")
    @TableLogic
    private Boolean isDelete;

    @Schema(description = "类型")
    private Integer type;

    @Schema(description = "状态")
    private Integer state;

    @Schema(description = "标签")
    private String label;

    @Schema(description = "排序")
    private Integer sorting;
}
