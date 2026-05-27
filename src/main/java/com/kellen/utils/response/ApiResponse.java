package com.kellen.utils.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kellen.utils.enumeration.ReturnCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一接口响应对象
 *
 * @param <T>: 业务响应数据类型
 * @author 孙凯伦
 * @DateTime 2026/5/26 下午
 * @email 376253703@qq.com
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一接口响应对象")
public class ApiResponse<T> {

    /**
     * 是否成功
     */
    @Schema(name = "success", description = "是否成功")
    private Boolean success = true;

    /**
     * 统一错误码
     */
    @Schema(name = "code", description = "统一错误码")
    private String code = ReturnCode.成功.getState();

    /**
     * 错误码默认提示
     */
    @Schema(name = "msg", description = "错误码默认提示")
    private String msg = ReturnCode.成功.getName();

    /**
     * 业务响应数据
     */
    @Schema(name = "data", description = "业务响应数据")
    private T data = null;

    /**
     * 失败时的稳定错误提示
     */
    @Schema(name = "errorMessage", description = "失败时的稳定错误提示")
    private String errorMessage = null;

    /**
     * 服务端响应时间
     */
    @Schema(name = "timestamp", description = "服务端响应时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 无参构造器
     *
     * @return
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    public ApiResponse() {
        init(ReturnCode.成功, null, null); // 默认对象表示成功空响应，保证 success、code、msg 三者一致。
    }

    /**
     * 成功空响应
     *
     * @return com.kellen.utils.response.ApiResponse<java.lang.Void>
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    public static ApiResponse<Void> success() {
        return success(null); // 空成功响应复用成功数据响应入口，保证字段初始化规则一致。
    }

    /**
     * 成功数据响应
     *
     * @param data: 返回给前端的业务数据
     * @return com.kellen.utils.response.ApiResponse<T>
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>(); // 创建标准响应对象，避免外部直接拼装返回结构。
        response.init(ReturnCode.成功, data, null); // 成功响应使用统一成功码，并写入业务数据。
        return response; // 返回完整统一响应对象。
    }

    /**
     * 失败响应
     *
     * @param returnCode: 失败业务返回码
     * @return com.kellen.utils.response.ApiResponse<java.lang.Void>
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    public static ApiResponse<Void> fail(ReturnCode returnCode) {
        return fail(returnCode, null); // 无自定义错误提示时回退错误码默认提示。
    }

    /**
     * 失败响应
     *
     * @param returnCode: 失败业务返回码
     * @param error:      返回给前端的稳定错误提示
     * @return com.kellen.utils.response.ApiResponse<java.lang.Void>
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    public static ApiResponse<Void> fail(ReturnCode returnCode, String error) {
        ApiResponse<Void> response = new ApiResponse<>(); // 创建标准响应对象，避免异常处理器直接手写字段。
        response.init(returnCode, null, error); // 失败响应不写 data，只写错误码和错误提示。
        return response; // 返回完整统一响应对象。
    }

    /**
     * 初始化响应字段
     *
     * @param returnCode: 业务返回码
     * @param data:       业务响应数据
     * @param error:      失败时的稳定错误提示
     * @return void
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    private void init(ReturnCode returnCode, T data, String error) {
        ReturnCode actualCode = returnCode == null ? ReturnCode.系统执行出错 : returnCode; // 返回码为空时兜底为系统错误，避免空指针响应。
        this.success = ReturnCode.成功 == actualCode; // success 只由统一返回码决定，避免调用方传入矛盾状态。
        this.code = actualCode.getState(); // code 使用 ReturnCode 中稳定编码，方便前端和日志定位。
        this.msg = actualCode.getName(); // msg 使用 ReturnCode 默认中文说明，保持所有服务文案一致。
        this.data = this.success ? data : null; // 失败响应不返回业务数据，避免前端误读失败结果。
        this.errorMessage = this.success ? null : defaultError(actualCode, error); // 成功不输出错误信息，失败优先输出业务提示。
        this.timestamp = LocalDateTime.now(); // 每次构造响应时记录服务端响应时间。
    }

    /**
     * 获取默认错误提示
     *
     * @param returnCode: 业务返回码
     * @param error:      自定义错误提示
     * @return java.lang.String
     * @author 孙凯伦
     * @DateTime 2026/5/26 下午
     * @email 376253703@qq.com
     */
    private String defaultError(ReturnCode returnCode, String error) {
        return error == null || error.isBlank() ? returnCode.getName() : error; // 自定义提示为空时使用错误码默认提示。
    }
}
