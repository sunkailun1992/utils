package com.kellen.utils.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kellen.observability.TraceContextSnapshot;
import com.kellen.utils.enumeration.ReturnCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 统一接口响应对象
 *
 * @param <T>: 业务响应数据类型
 * @author 孙凯伦
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "统一接口响应对象")
public class ApiResponse<T> {

    /**
     * 响应时间使用的固定时区。
     */
    private static final ZoneOffset GMT_8 = ZoneOffset.ofHours(8);

    /**
     * 是否成功
     */
    @Schema(name = "success", description = "是否成功")
    private Boolean success = true;

    /**
     * 统一错误码
     */
    @Schema(name = "code", description = "统一错误码")
    private String code = ReturnCode.SUCCESS.getCode();

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
     * 当前请求的 OTel 追踪号，与响应头 traceId 同值；没有活动链路时不输出
     */
    @Schema(name = "traceId", description = "当前请求的追踪号，排查问题时使用；没有时不输出")
    private String traceId = null;

    /**
     * 服务端响应时间，固定按 GMT+8 取值，不随 JVM 时区变化
     */
    @Schema(name = "timestamp", description = "服务端响应时间（GMT+8）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timestamp = now();

    /**
     * 无参构造器
     *
     * @return
     * @author 孙凯伦
     */
    public ApiResponse() {
        init(ReturnCode.SUCCESS, null, null); // 默认对象表示成功空响应，保证 success 与 code 一致。
    }

    /**
     * 成功空响应
     *
     * @return com.kellen.utils.response.ApiResponse<java.lang.Void>
     * @author 孙凯伦
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
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>(); // 创建标准响应对象，避免外部直接拼装返回结构。
        response.init(ReturnCode.SUCCESS, data, null); // 成功响应使用统一成功码，并写入业务数据。
        return response; // 返回完整统一响应对象。
    }

    /**
     * 失败响应
     *
     * @param returnCode: 失败业务返回码
     * @param <T>         接口声明的数据类型，失败时 data 恒为 null
     * @return com.kellen.utils.response.ApiResponse<T>
     * @author 孙凯伦
     */
    public static <T> ApiResponse<T> fail(ReturnCode returnCode) {
        return fail(returnCode, null); // 无自定义错误提示时回退错误码默认提示。
    }

    /**
     * 失败响应
     *
     * @param returnCode: 失败业务返回码
     * @param error:      返回给前端的稳定错误提示
     * @param <T>         接口声明的数据类型，失败时 data 恒为 null
     * @return com.kellen.utils.response.ApiResponse<T>
     * @author 孙凯伦
     */
    public static <T> ApiResponse<T> fail(ReturnCode returnCode, String error) {
        ApiResponse<T> response = new ApiResponse<>(); // 创建标准响应对象，避免异常处理器直接手写字段。
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
     */
    private void init(ReturnCode returnCode, T data, String error) {
        ReturnCode actualCode = returnCode == null ? ReturnCode.SYSTEM_EXECUTION_ERROR : returnCode; // 返回码为空时兜底为系统错误，避免空指针响应。
        this.success = ReturnCode.SUCCESS == actualCode; // success 只由统一返回码决定，避免调用方传入矛盾状态。
        this.code = actualCode.getCode(); // code 使用 ReturnCode 中稳定编码，方便前端和日志定位。
        this.data = this.success ? data : null; // 失败响应不返回业务数据，避免前端误读失败结果。
        this.errorMessage = this.success ? null : defaultError(actualCode, error); // 成功不输出错误信息，失败优先输出业务提示。
        this.traceId = TraceContextSnapshot.capture().traceId(); // 取当前 OTel 链路的追踪号，没有活动链路时为 null。
        this.timestamp = now(); // 每次构造响应时记录服务端响应时间。
    }

    /**
     * 获取默认错误提示
     *
     * @param returnCode: 业务返回码
     * @param error:      自定义错误提示
     * @return java.lang.String
     * @author 孙凯伦
     */
    private String defaultError(ReturnCode returnCode, String error) {
        return error == null || error.isBlank() ? returnCode.getMessage() : error; // 自定义提示为空时使用错误码默认提示。
    }

    /**
     * 按 GMT+8 取当前时间；{@code @JsonFormat} 的 timezone 对 {@link LocalDateTime} 不起作用，只能在取值时固定。
     *
     * @return GMT+8 的当前时间
     */
    private static LocalDateTime now() {
        return LocalDateTime.now(GMT_8);
    }
}
