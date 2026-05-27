package com.kellen.utils.exception;

import com.kellen.utils.response.ApiResponse;
import com.kellen.utils.enumeration.ReturnCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 统一异常响应处理器。
 *
 * <p>当前体系不再依赖历史 token 请求头或 Redis token 用户模式判断是否包装异常，
 * 所有进入全局异常处理器的异常都统一转换为 {@link ApiResponse}。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
public class ApiExceptionHandler {

    /**
     * 用户异常处理。
     *
     * @param e       用户异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = UserException.class)
    public ApiResponse<Void> userException(UserException e, HttpServletRequest request) {
        ReturnCode returnCode = Objects.nonNull(e.getReturnCode()) ? e.getReturnCode() : ReturnCode.用户端错误; // 优先使用业务主动传入的错误码。
        return fail(request, "UserException", e, returnCode); // 统一记录日志并组装 ApiResponse。
    }

    /**
     * 防重复提交异常处理。
     *
     * @param e       防重复提交异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = PreventRepeatException.class)
    public ApiResponse<Void> preventRepeatException(PreventRepeatException e, HttpServletRequest request) {
        return fail(request, "PreventRepeatException", e, ReturnCode.用户重复请求); // 幂等锁命中时返回用户重复请求错误码。
    }

    /**
     * 必填参数为空异常处理。
     *
     * @param e       参数异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = ParameterNullException.class)
    public ApiResponse<Void> parameterNullException(ParameterNullException e, HttpServletRequest request) {
        return fail(request, "ParameterNullException", e, ReturnCode.请求必填参数为空); // 参数为空时返回统一参数错误码。
    }

    /**
     * 业务异常处理。
     *
     * @param e       业务异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> businessException(BusinessException e, HttpServletRequest request) {
        return fail(request, "BusinessException", e, ReturnCode.系统执行出错); // 未细分业务异常默认归类为系统执行错误。
    }

    /**
     * RPC调用异常处理。
     *
     * @param e       RPC异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(RpcException.class)
    public ApiResponse<Void> rpcException(RpcException e, HttpServletRequest request) {
        return fail(request, "RpcException", e, ReturnCode.RPC服务出错); // 下游服务调用失败时返回 RPC 服务错误码。
    }

    /**
     * 短信通知异常处理。
     *
     * @param e       短信异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = SmsException.class)
    public ApiResponse<Void> smsException(SmsException e, HttpServletRequest request) {
        return fail(request, "SmsException", e, ReturnCode.通知服务出错); // 短信发送失败归类为通知服务错误。
    }

    /**
     * 时间参数异常处理。
     *
     * @param e       时间异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = DateException.class)
    public ApiResponse<Void> dateFmtException(DateException e, HttpServletRequest request) {
        return fail(request, "DateException", e, ReturnCode.非法的时间戳参数); // 时间格式或时间戳异常返回时间戳参数错误。
    }

    /**
     * RSA签名异常处理。
     *
     * @param e       签名异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = SignException.class)
    public ApiResponse<Void> signException(SignException e, HttpServletRequest request) {
        return fail(request, "SignException", e, ReturnCode.RSA签名错误); // 签名验签失败返回 RSA 签名错误码。
    }

    /**
     * SQL异常处理。
     *
     * @param e       SQL异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler({SQLException.class})
    public ApiResponse<Void> sqlException(SQLException e, HttpServletRequest request) {
        return fail(request, "SQLException", e, ReturnCode.数据库服务出错); // 数据库访问异常统一转换为数据库服务错误。
    }

    /**
     * JSR参数校验异常处理。
     *
     * @param e       参数校验异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    protected ApiResponse<Void> handlerConstraintviolationException(ConstraintViolationException e, HttpServletRequest request) {
        List<String> msgList = new ArrayList<>(); // 收集所有字段校验错误，避免只返回第一条。
        Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations(); // 读取 jakarta validation 的约束失败集合。
        for (ConstraintViolation<?> next : constraintViolations) { // 逐条提取校验提示。
            msgList.add(next.getMessageTemplate()); // 保留注解里配置的稳定提示文案。
        }
        return fail(request, "ConstraintViolationException", e, ReturnCode.请求必填参数为空, msgList.toString()); // 返回统一参数错误响应。
    }

    /**
     * 请求体参数校验异常处理。
     *
     * @param e       请求体参数校验异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ApiResponse<Void> handlerMethodArgumentnotvalidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<String> msgList = dealBindResult(e.getBindingResult()); // 从 Spring BindingResult 中提取字段错误提示。
        return fail(request, "MethodArgumentNotValidException", e, ReturnCode.请求必填参数为空, msgList.toString()); // 返回统一参数错误响应。
    }

    /**
     * 表单或查询参数绑定异常处理。
     *
     * @param e       绑定异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(BindException.class)
    protected ApiResponse<Void> handlerBindException(BindException e, HttpServletRequest request) {
        List<String> msgList = dealBindResult(e.getBindingResult()); // 从 Spring BindingResult 中提取字段错误提示。
        return fail(request, "BindException", e, ReturnCode.请求必填参数为空, msgList.toString()); // 返回统一参数错误响应。
    }

    /**
     * 客户模块异常处理。
     *
     * @param e       客户模块异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = CustomerException.class)
    public ApiResponse<Void> customerException(CustomerException e, HttpServletRequest request) {
        return fail(request, "CustomerException", e, ReturnCode.用户端错误); // 客户模块异常归类为用户端错误。
    }

    /**
     * 从绑定结果中提取错误提示。
     *
     * @param bindingResult Spring 参数绑定结果
     * @return 错误提示列表
     */
    private List<String> dealBindResult(BindingResult bindingResult) {
        List<String> msgList = new ArrayList<>(); // 创建错误提示列表。
        List<ObjectError> allErrors = bindingResult.getAllErrors(); // 获取所有对象级和字段级错误。
        for (ObjectError objectError : allErrors) { // 逐条处理参数错误。
            msgList.add(objectError.getDefaultMessage()); // 使用校验注解配置的默认提示。
        }
        return msgList; // 返回所有错误提示，供 ApiResponse.errorMessage 使用。
    }

    /**
     * 记录异常并构造统一失败响应。
     *
     * @param request    当前HTTP请求
     * @param name       异常名称
     * @param e          异常对象
     * @param returnCode 统一错误码
     * @return 统一失败响应
     */
    private ApiResponse<Void> fail(HttpServletRequest request, String name, Exception e, ReturnCode returnCode) {
        return fail(request, name, e, returnCode, e.getMessage()); // 默认使用异常消息作为稳定错误提示。
    }

    /**
     * 记录异常并构造统一失败响应。
     *
     * @param request      当前HTTP请求
     * @param name         异常名称
     * @param e            异常对象
     * @param returnCode   统一错误码
     * @param errorMessage 稳定错误提示
     * @return 统一失败响应
     */
    private ApiResponse<Void> fail(HttpServletRequest request, String name, Exception e, ReturnCode returnCode, String errorMessage) {
        log.error("{} start, uri: [{}], exception: [{}], caused by: [{}]", name, request.getRequestURI(), e.getClass(), e.getMessage()); // 记录异常类型、请求地址和错误摘要，避免依赖 token 判断是否记录。
        return ApiResponse.fail(returnCode, errorMessage); // 使用 ApiResponse 统一失败工厂方法组装响应。
    }
}
