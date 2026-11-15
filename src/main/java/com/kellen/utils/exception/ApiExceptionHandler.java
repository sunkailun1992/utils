package com.kellen.utils.exception;

import com.kellen.utils.response.ApiResponse;
import com.kellen.utils.enumeration.ReturnCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 统一异常响应处理器。
 *
 * <p>继承 Spring 的 {@link ResponseEntityExceptionHandler}：Spring MVC 标准异常保留 Spring 给的 HTTP 状态码，
 * 报文换成 {@link ApiResponse}；未知异常回 500；Spring Security 的鉴权异常原样抛出交给 Spring Security 处理；
 * 业务与参数校验异常仍回 HTTP 200。SQL 与 RPC 异常只返回固定文案，不把底层报错原文交给客户端。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 用户异常处理。
     *
     * @param e       用户异常
     * @param request 当前HTTP请求
     * @return 统一失败响应
     */
    @ExceptionHandler(value = UserException.class)
    public ApiResponse<Void> userException(UserException e, HttpServletRequest request) {
        ReturnCode returnCode = Objects.nonNull(e.getReturnCode()) ? e.getReturnCode() : ReturnCode.CLIENT_ERROR; // 优先使用业务主动传入的错误码。
        return fail(request, "UserException", e, returnCode); // 统一记录日志并组装 ApiResponse。
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
        return fail(request, "BusinessException", e, ReturnCode.SYSTEM_EXECUTION_ERROR); // 未细分业务异常默认归类为系统执行错误。
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
        return fail(request, "RpcException", e, ReturnCode.RPC_SERVICE_ERROR, null); // 下游报错原文只进日志，客户端只看到固定文案。
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
        log.error("SQLException, uri: [{}], sqlState: [{}], vendorCode: [{}]", request.getRequestURI(), e.getSQLState(), e.getErrorCode()); // SQL 报错原文可能带表名与数据值，不写日志也不返回。
        return ApiResponse.fail(ReturnCode.DATABASE_SERVICE_ERROR); // 客户端只看到固定的数据库服务错误文案。
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
        return fail(request, "ConstraintViolationException", e, ReturnCode.REQUIRED_PARAMETER_MISSING, msgList.toString()); // 返回统一参数错误响应。
    }

    /**
     * 请求体参数校验异常处理：仍回 HTTP 200 与请求必填参数为空错误码，提示为全部字段校验信息。
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<String> msgList = dealBindResult(ex.getBindingResult()); // 从 Spring BindingResult 中提取字段错误提示。
        return ResponseEntity.ok(fail(servletRequest(request), "MethodArgumentNotValidException", ex,
                ReturnCode.REQUIRED_PARAMETER_MISSING, msgList.toString()));
    }

    /**
     * 兜底处理未知异常：Spring Security 的鉴权异常原样抛出，其余按 HTTP 500 返回统一报文。
     *
     * @param e       异常
     * @param request 当前请求
     * @return 统一失败响应
     * @throws Exception 鉴权异常原样抛出
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unhandledException(Exception e, WebRequest request) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e; // 交回 Spring Security，未登录回 401、无权限回 403。
        }
        return handleExceptionInternal(e, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * 沿用 Spring 的状态码与响应头，把报文换成统一格式；响应已开始输出时不再写报文。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> standard = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (standard == null) {
            return null;
        }
        String uri = servletRequest(request).getRequestURI();
        if (statusCode.is5xxServerError()) {
            log.error("Request failed, uri: [{}], status: [{}]", uri, statusCode.value(), ex);
        } else {
            log.warn("Request rejected, uri: [{}], status: [{}], exception: [{}]", uri, statusCode.value(), ex.getClass().getName());
        }
        return ResponseEntity.status(standard.getStatusCode()).headers(standard.getHeaders())
                .body(ApiResponse.fail(returnCode(statusCode), message(ex, statusCode)));
    }

    private static ReturnCode returnCode(HttpStatusCode status) {
        if (status.value() == 400) {
            return ReturnCode.REQUEST_PARAMETER_ERROR;
        }
        return status.is4xxClientError() ? ReturnCode.CLIENT_ERROR : ReturnCode.SYSTEM_EXECUTION_ERROR;
    }

    private static String message(Exception ex, HttpStatusCode status) {
        if (status.value() == 400) {
            return ex instanceof HttpMessageNotReadableException ? "请求体 JSON 格式不正确" : "请求参数不正确";
        }
        if (status.value() == 404) {
            return "请求的接口不存在";
        }
        return status.is4xxClientError() ? "请求无法处理" : "系统处理失败，请查看服务日志";
    }

    private static HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
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
        return fail(request, "BindException", e, ReturnCode.REQUIRED_PARAMETER_MISSING, msgList.toString()); // 返回统一参数错误响应。
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
