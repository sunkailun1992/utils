package com.gb.log.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

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
@ApiModel(value = "日志实体类")
public class RequestLog implements Serializable {
    @Id
    @ApiModelProperty(name = "_id", value = "序列")
    @SuppressWarnings(value = "all")
    private ObjectId _id;

    @ApiModelProperty(name = "userId", value = "用户id")
    private Long userId;

    @ApiModelProperty(name = "username", value = "账号")
    private String username;

    @ApiModelProperty(name = "name", value = "用户名称")
    private String name;

    @ApiModelProperty(name = "url", value = "请求地址")
    private String url;

    @ApiModelProperty(name = "elapsedTime", value = "消耗时间")
    private Long elapsedTime;

    @ApiModelProperty(name = "request", value = "请求参数")
    private Object request;

    @ApiModelProperty(name = "results", value = "返回结果")
    private Object results;

    @ApiModelProperty(name = "interfaceName", value = "接口名称")
    private String interfaceName;

    @ApiModelProperty(name = "performBefore", value = "执行前")
    private String performBefore;

    @ApiModelProperty(name = "performAfter", value = "执行后")
    private String performAfter;

    @ApiModelProperty(name = "system", value = "系统名称")
    private String systemName;

    @ApiModelProperty(name = "ip", value = "ip地址")
    private String ip;

    @ApiModelProperty(value = "说明")
    private String description;

    @ApiModelProperty(value = "环境")
    private String environment;

    @ApiModelProperty(value = "创建时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateTime;

    @ApiModelProperty(value = "创建人")
    private String createName;

    @ApiModelProperty(value = "修改时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime modifyDateTime;

    @ApiModelProperty(value = "修改人")
    private String modifyName;

    @ApiModelProperty(value = "删除状态（0：未删除，1：删除）")
    @TableLogic
    private Boolean isDelete;

    @ApiModelProperty(value = "类型")
    private Integer type;

    @ApiModelProperty(value = "状态")
    private Integer state;

    @ApiModelProperty(value = "标签")
    private String label;

    @ApiModelProperty(value = "排序")
    private Integer sorting;
}
