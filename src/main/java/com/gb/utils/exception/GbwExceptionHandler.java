package com.gb.utils.exception;

import cn.hutool.core.convert.Convert;
import com.gb.utils.Json;
import com.gb.utils.enumeration.ReturnCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @ClassName GbwExceptionHandler
 * @Description 工保网异常处理
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/6/10 2:55 下午
 */
@Slf4j
public class GbwExceptionHandler {

    public final static String TOKEN = "token";

    public final static String RPC_EXCEPTION = "rpcException";

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: userException
     * @description: TODO  用户异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:21 下午
     */
    @ExceptionHandler(value = UserException.class)
    public Json userException(UserException e, HttpServletRequest request) throws UserException {
        //有ReturnCode
        if(Objects.nonNull(e.getReturnCode())) {
            return new Json(e.getReturnCode(), e.getMessage());
        }
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("UserException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.用户端错误, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("UserException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.用户端错误, e.getMessage());
            }
        }
        throw e;
    }

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: preventRepeatException
     * @description: TODO  幂等异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:21 下午
     */
    @ExceptionHandler(value = PreventRepeatException.class)
    public Json preventRepeatException(PreventRepeatException e, HttpServletRequest request) throws PreventRepeatException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("PreventRepeatException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.用户重复请求, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("PreventRepeatException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.用户重复请求, e.getMessage());
            }
        }
        throw e;
    }

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: parameterNullException
     * @description: TODO  参数为空异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:22 下午
     */
    @ExceptionHandler(value = ParameterNullException.class)
    public Json parameterNullException(ParameterNullException e, HttpServletRequest request) throws ParameterNullException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("ParameterNullException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.请求必填参数为空, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("ParameterNullException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.请求必填参数为空, e.getMessage());
            }
        }
        throw e;
    }

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: VersionException
     * @description: TODO  版本异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:22 下午
     */
    @ExceptionHandler(value = VersionException.class)
    public Json versionException(VersionException e, HttpServletRequest request) throws VersionException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("VersionException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.用户API请求版本不匹配, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("VersionException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.用户API请求版本不匹配, e.getMessage());
            }
        }
        throw e;
    }

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: smsException
     * @description: TODO  业务异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:22 下午
     */
    @ExceptionHandler(BusinessException.class)
    public Json businessException(BusinessException e, HttpServletRequest request) {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("BusinessException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.系统执行出错, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("BusinessException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.系统执行出错, e.getMessage());
            }
        }
        throw e;
    }

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: smsException
     * @description: TODO  rpc调用异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:22 下午
     */
    @ExceptionHandler(RpcException.class)
    public Json rpcException(RpcException e, HttpServletRequest request) throws RpcException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("RpcException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.RPC服务出错, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("RpcException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.RPC服务出错, e.getMessage());
            }
        }
        throw e;
    }

    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: smsException
     * @description: TODO  发送短信异常
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:22 下午
     */
    @ExceptionHandler(value = SmsException.class)
    public Json smsException(SmsException e, HttpServletRequest request) throws SmsException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("SmsException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.通知服务出错, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("SmsException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.通知服务出错, e.getMessage());
            }
        }
        throw e;
    }



    /**
     * @param e
     * @param request
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: dateFmtException
     * @description: TODO 时间参数异常
     * @return: com.gb.utils.Json
     * @date: 2021/8/5 11:25 上午
     */
    @ExceptionHandler(value = DateException.class)
    public Json dateFmtException(ParameterNullException e, HttpServletRequest request) throws ParameterNullException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("ParameterNullException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.非法的时间戳参数, e.getMessage());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("ParameterNullException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.非法的时间戳参数, e.getMessage());
            }
        }
        throw e;
    }


    /**
     *
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: smsException
     * @description: TODO  签名异常
     * @param e
     * @param request
     * @return: com.gb.utils.Json
     * @date: 2021/4/1 2:22 下午
     *
     */
    @ExceptionHandler(value = SignException.class)
    public Json signException(SignException e, HttpServletRequest request) throws SignException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("SignException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
            return new Json(ReturnCode.RSA签名错误, e.getMessage());
        }
        //rpc是否要返回异常
        if(StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))){
            if(Convert.toBool(request.getParameter(RPC_EXCEPTION))){
                log.error("SignException start, uri: [{}], exception: [{}], caused by: [{}]", request.getRequestURI(), e.getClass(), e.getMessage());
                return new Json(ReturnCode.RSA签名错误, e.getMessage());
            }
        }
        throw e;
    }


    /**
     *
     * @auther: 孙凯伦
     * 
     * @email: 376253703@qq.com
     * @name: sqlException
     * @description: TODO  sql异常
     * @param e
     * @param request
     * @return: com.gb.utils.Json
     * @date: 2021/8/5 2:59 下午
     *
     */
    @ExceptionHandler({SQLException.class})
    public Json sqlException(SQLException e, HttpServletRequest request) throws SQLException {
        if (StringUtils.isNotBlank(request.getHeader("TOKEN"))) {
            log.error("SQLException start, uri: [{}], exception: [{}], caused by: [{}]", new Object[]{request.getRequestURI(), e.getClass(), e.getMessage()});
            return new Json(ReturnCode.数据库服务出错, e.getMessage());
        } else if (StringUtils.isNotBlank(request.getParameter("RPC_EXCEPTION")) && Convert.toBool(request.getParameter("RPC_EXCEPTION"))) {
            log.error("SQLException start, uri: [{}], exception: [{}], caused by: [{}]", new Object[]{request.getRequestURI(), e.getClass(), e.getMessage()});
            return new Json(ReturnCode.数据库服务出错, e.getMessage());
        } else {
            throw e;
        }
    }


    /**
     * 处理JSR标准的参数校验错误
     */
    @ExceptionHandler(ConstraintViolationException.class)
    protected Json handlerConstraintviolationException(ConstraintViolationException e, HttpServletRequest request) {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("handlerConstraintviolationException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
            List<String> msgList = new ArrayList<>();
            Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
            for (ConstraintViolation<?> next : constraintViolations) {
                msgList.add(next.getMessageTemplate());
            }
            return new Json(ReturnCode.请求必填参数为空, msgList.toString());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("handlerConstraintviolationException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
                List<String> msgList = new ArrayList<>();
                Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
                for (ConstraintViolation<?> next : constraintViolations) {
                    msgList.add(next.getMessageTemplate());
                }
                return new Json(ReturnCode.请求必填参数为空, msgList.toString());
            }
        }
        throw e;
    }


    /**
     * 处理Hibernate扩展校验注解异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected Json handlerMethodArgumentnotvalidException(MethodArgumentNotValidException e, HttpServletRequest request) throws MethodArgumentNotValidException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("handlerMethodArgumentnotvalidException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
            List<String> msgList = dealBindResult(e.getBindingResult());
            return new Json(ReturnCode.请求必填参数为空, msgList.toString());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("handlerMethodArgumentnotvalidException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
                List<String> msgList = dealBindResult(e.getBindingResult());
                return new Json(ReturnCode.请求必填参数为空, msgList.toString());
            }
        }
        throw e;
    }


    /**
     * 处理数据绑定异常
     */
    @ExceptionHandler(BindException.class)
    protected Json handlerBindException(BindException e, HttpServletRequest request) throws BindException {
        //有token
        if (StringUtils.isNotBlank(request.getHeader(TOKEN))) {
            log.error("handlerBindException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
            List<String> msgList = dealBindResult(e.getBindingResult());
            return new Json(ReturnCode.请求必填参数为空, msgList.toString());
        }
        //rpc是否要返回异常
        if (StringUtils.isNotBlank(request.getParameter(RPC_EXCEPTION))) {
            if (Convert.toBool(request.getParameter(RPC_EXCEPTION))) {
                log.error("handlerBindException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
                List<String> msgList = dealBindResult(e.getBindingResult());
                return new Json(ReturnCode.请求必填参数为空, msgList.toString());
            }
        }
        throw e;
    }

    /**
     * 客户模块异常
     * 无需token
     */
    @ExceptionHandler(value = CustomerException.class)
    public Json customerException(CustomerException e, HttpServletRequest request) throws CustomerException {
        log.error("customerException start, uri: [{}], caused by: [{}]", request.getRequestURI(), e);
        return new Json(ReturnCode.用户端错误, e.getMessage());
    }


    private List<String> dealBindResult(BindingResult bindingResult) {
        List<String> msgList = new ArrayList<>();
        List<ObjectError> allErrors = bindingResult.getAllErrors();
        for (ObjectError objectError : allErrors) {
            msgList.add(objectError.getDefaultMessage());
        }
        return msgList;
    }


}
