package com.kellen.log.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.format.annotation.DateTimeFormat;

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
@Document(indexName = "request_log")
public class ElasticSearchRequestLog {
    @Id
    private String id;

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
    private String request;

    @Schema(name = "results", description = "返回结果")
    private String results;

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
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateTime;

    @Schema(description = "创建人")
    private String createName;

}
