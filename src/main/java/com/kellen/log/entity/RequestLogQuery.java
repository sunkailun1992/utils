package com.kellen.log.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 *
 * @author 孙凯伦
 * @DateTime 2018/7/16  上午10:10
 * @email 376253703@qq.com
 * 
 * @explain
 */
@Data
@Schema(description = "日志实体类")
public class RequestLogQuery implements Serializable {

    /**
     * 用户id
     */
    @Schema(name = "operationLog", description = "用户id")
    private RequestLog requestLog;
    /**
     * 查询url
     */
    @Schema(name = "urlList", description = "查询url")
    private List<String> urlList;
    /**
     * 开始时间
     */
    @Schema(name = "createDateStart", description = "开始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateStart;
    /**
     * 结束时间
     */
    @Schema(name = "createDateEnd", description = "结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateEnd;
    /**
     * 分页和排序
     */
    @Schema(name = "pageable", description = "分页和排序")
    private Pageable pageable;
    /**
     * 接口名称
     */
    @Schema(name = "interfaceName", description = "接口名称")
    private String interfaceName;
    /**
     * ip地址
     */
    @Schema(name = "ip", description = "ip地址")
    private String ip;
    /**
     * 操作人名称
     */
    @Schema(name = "name", description = "用户名称")
    private String name;
    /**
     * 系统名称
     */
    @Schema(name = "system", description = "系统名称")
    private String systemName;
    /**
     * 用户ID
     */
    @Schema(name = "userId", description = "用户id")
    private Long userId;
    /**
     * 降序字段
     */
    @Schema(name = "fieldDesc", description = "降序字段")
    private String fieldDesc;

}
