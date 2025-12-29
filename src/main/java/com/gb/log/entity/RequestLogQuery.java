package com.gb.log.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

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
public class RequestLogQuery implements Serializable {

    /**
     * 用户id
     */
    @ApiModelProperty(name = "operationLog", value = "用户id")
    private RequestLog requestLog;
    /**
     * 查询url
     */
    @ApiModelProperty(name = "urlList", value = "查询url")
    private List<String> urlList;
    /**
     * 开始时间
     */
    @ApiModelProperty(name = "createDateStart", value = "开始时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateStart;
    /**
     * 结束时间
     */
    @ApiModelProperty(name = "createDateEnd", value = "结束时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createDateEnd;
    /**
     * 分页和排序
     */
    @ApiModelProperty(name = "pageable", value = "分页和排序")
    private Pageable pageable;
    /**
     * 接口名称
     */
    @ApiModelProperty(name = "interfaceName", value = "接口名称")
    private String interfaceName;
    /**
     * ip地址
     */
    @ApiModelProperty(name = "ip", value = "ip地址")
    private String ip;
    /**
     * 操作人名称
     */
    @ApiModelProperty(name = "name", value = "用户名称")
    private String name;
    /**
     * 系统名称
     */
    @ApiModelProperty(name = "system", value = "系统名称")
    private String systemName;
    /**
     * 用户ID
     */
    @ApiModelProperty(name = "userId", value = "用户id")
    private Long userId;
    /**
     * 降序字段
     */
    @ApiModelProperty(name = "fieldDesc", value = "降序字段")
    private String fieldDesc;

}
