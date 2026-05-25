package com.kellen.log.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "rpc日志实体类")
public class RpcLog implements Serializable {
    @Id
    @Schema(name = "_id", description = "序列")
    @SuppressWarnings("all")
    private ObjectId _id;

    @Schema(name = "sendServer", description = "发送服务")
    private String sendServer;

    @Schema(name = "sendServer", description = "接收服务")
    private String receiveServer;

    @Schema(name = "parameter", description = "参数")
    private Object parameter;

    @Schema(name = "error", description = "错误")
    private String error;

    @Schema(name = "api", description = "调用接口")
    private String api;

    @Schema(description = "说明")
    private String description;

    @Schema(description = "创建时间")
    private Date createDateTime;

    @Schema(description = "创建人")
    private String createName;

    @Schema(description = "修改时间")
    private Date modifyDateTime;

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
