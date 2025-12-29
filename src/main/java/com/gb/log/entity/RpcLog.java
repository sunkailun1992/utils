package com.gb.log.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

import java.io.Serializable;
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
@ApiModel(value = "rpc日志实体类")
public class RpcLog implements Serializable {
    @Id
    @ApiModelProperty(name = "_id", value = "序列")
    @SuppressWarnings(value = "all")
    private ObjectId _id;

    @ApiModelProperty(name = "sendServer", value = "发送服务")
    private String sendServer;

    @ApiModelProperty(name = "sendServer", value = "接收服务")
    private String receiveServer;

    @ApiModelProperty(name = "parameter", value = "参数")
    private Object parameter;

    @ApiModelProperty(name = "error", value = "错误")
    private String error;

    @ApiModelProperty(name = "api", value = "调用接口")
    private String api;

    @ApiModelProperty(value = "说明")
    private String description;

    @ApiModelProperty(value = "创建时间")
    private Date createDateTime;

    @ApiModelProperty(value = "创建人")
    private String createName;

    @ApiModelProperty(value = "修改时间")
    private Date modifyDateTime;

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
