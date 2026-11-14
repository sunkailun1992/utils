package com.kellen.aop;

import com.kellen.observability.TraceContextSnapshot;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import com.kellen.idempotent.PreventRepeatInit;
import com.kellen.log.entity.ElasticSearchRequestLog;
import com.kellen.log.service.ElasticSearchRequestLogService;
import com.kellen.utils.context.DynamicSourceTtl;
import com.kellen.utils.http.RequestUtil;
import com.kellen.utils.exception.BusinessException;
import com.kellen.utils.exception.PreventRepeatException;
import com.kellen.utils.methods.MethodsJudge;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 请求通用切面。
 *
 * <p>统一处理 SQL 参数校验、动态数据源切换、防重复提交、请求日志和慢请求钉钉提醒。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
@Aspect
@Order(1)
@Component
public class RequestRequiredAspect {

    /**
     * 防重复提交处理器。
     */
    @Autowired
    private PreventRepeatInit preventRepeatInit;

    /**
     * Elasticsearch 请求日志服务。
     */
    @Autowired
    private ElasticSearchRequestLogService elasticSearchRequestLogService;

    /**
     * 匹配标记了 RequestRequired 的 Controller 或服务类。
     */
    @Pointcut("within(@com.kellen.utils.annotations.RequestRequired *)")
    public void pointcut() {

    }

    /**
     * 方法执行前处理请求上下文。
     *
     * @param point 切点
     * @throws Exception 前置处理异常
     */
    @Before("pointcut()")
    public void before(JoinPoint point) throws Exception {
        HttpServletRequest request = getHttpServletRequest(); // 获取当前请求对象。
        verify(request); // 校验排序字段等 SQL 拼接参数。
        String dataSource = getDataSource(request); // 从请求头解析动态数据源。
        preventRepeatInit.init(point); // 保留防重复提交能力，但内部不再依赖旧 token。
        DynamicSourceTtl.push(dataSource); // 写入动态数据源上下文。
    }

    /**
     * 方法正常完成后清理上下文。
     *
     * @param joinPoint 切点
     * @throws Exception 后置处理异常
     */
    @After("pointcut()")
    public void after(JoinPoint joinPoint) throws Exception {
        preventRepeatInit.delete(joinPoint); // 方法正常结束后释放本次 @PreventRepeat 生成的幂等锁。
        DynamicSourceTtl.clear(); // 清理数据源上下文，避免线程复用串数据源。
    }

    /**
     * 环绕处理请求日志。
     *
     * @param proceedingJoinPoint 环绕切点
     * @return 业务方法返回值
     * @throws Throwable 业务方法或切面异常
     */
    @Around("pointcut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        HttpServletRequest request = getHttpServletRequest(); // 获取当前请求对象。
        long start = System.currentTimeMillis(); // 记录开始时间。
        String performBefore = MethodsJudge.performBefore(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName(), RequestUtil.getParameterMap(request)); // 执行 Methods 前置扩展点。
        Object o = proceedingJoinPoint.proceed(); // 执行业务方法。
        long end = System.currentTimeMillis(); // 记录结束时间。
        String performAfter = MethodsJudge.performAfter(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName(), RequestUtil.getParameterMap(request)); // 执行 Methods 后置扩展点。
        log.debug("耗时:{}", end - start); // 输出接口耗时调试日志。
        setLog(proceedingJoinPoint, request, (end - start), RequestUtil.getParameterMap(request), o, performBefore, performAfter); // 日志落库不再要求旧 token 存在。
        return o; // 返回业务方法结果。
    }

    /**
     * 异常增强
     *
     * @param :
     * @return void
     * @author 孙凯伦
     */
    @AfterThrowing(value = "pointcut()", throwing = "e")
    public void afterThrow(JoinPoint joinPoint, Throwable e) throws Exception {
        //幂等异常不删除，保留锁到过期时间
        boolean isRepeatEx = e instanceof PreventRepeatException; // 判断是否为防重复提交抛出的业务异常。
        if (e instanceof UndeclaredThrowableException) {
            isRepeatEx = ((UndeclaredThrowableException) e).getUndeclaredThrowable() instanceof PreventRepeatException; // 兼容 AOP 包装后的幂等异常。
        }
        if (!isRepeatEx) {
            //异常删除幂等缓存
            preventRepeatInit.delete(joinPoint); // 非幂等异常释放锁，避免业务异常导致用户长期无法重试。
        }
        //移除数据源变量，防止堆积
        DynamicSourceTtl.clear();
    }

    /**
     * 拦截器,设置日志
     *
     * @param httpServletRequest:
     * @return void
     * @author 孙凯伦
     */
    private void setLog(ProceedingJoinPoint proceedingJoinPoint, HttpServletRequest httpServletRequest, long time, Map<String, String> map, Object o, String performBefore, String performAfter) {
        // 传输层统一记录受控 IO；旧审计不序列化参数、返回对象、扩展点正文或身份资料。
        TraceContextSnapshot trace = TraceContextSnapshot.capture();
        ElasticSearchRequestLog entry = new ElasticSearchRequestLog();
        entry.setId(IdUtil.simpleUUID());
        entry.setCreateDateTime(LocalDateTime.now());
        entry.setTraceId(trace.traceId());
        entry.setSpanId(trace.spanId());
        entry.setUrl(safeRoute(httpServletRequest));
        entry.setElapsedTime(Math.max(0, time));
        entry.setRequest("{\"omitted\":\"policy\"}");
        entry.setResults("{\"omitted\":\"policy\"}");
        elasticSearchRequestLogService.insert(entry);
    }

    /** 审计只采用框架路由模板，不把动态 URI、查询参数或客户端 trace ID 提升为元数据。 */
    private String safeRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern == null) return "UNMATCHED";
        String route = pattern.toString();
        return route.length() <= 180 && route.matches("[/A-Za-z0-9_{}:*?.-]+") ? route : "UNMATCHED";
    }

    /**
     * 获取当前请求对象
     *
     * @param :
     * @return jakarta.servlet.http.HttpServletRequest
     * @author 孙凯伦
     */
    private HttpServletRequest getHttpServletRequest() {
        //获得请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes.getRequest();
    }

    /**
     * 从请求头读取动态数据源。
     *
     * @param request 当前HTTP请求
     * @return 数据源名称
     */
    private String getDataSource(HttpServletRequest request) throws Exception {
        String dataSource = "master"; // 默认使用主数据源。
        String requestDataSource = request.getHeader("dataSource"); // 从请求头读取数据源标识。
        if (StringUtils.isNotBlank(requestDataSource)) {
            dataSource = requestDataSource; // 使用请求头指定的数据源。
        }
        return dataSource; // 返回最终数据源。
    }

    /**
     * 校验可能参与 SQL 拼接的请求参数。
     *
     * @param request 当前HTTP请求
     */
    private static void verify(HttpServletRequest request) {
        String collationFields = request.getParameter("collationFields"); // 读取前端传入的排序字段。
        if (StringUtils.isNotBlank(collationFields)) {
            if (SqlInjectionUtils.check(collationFields)) {
                throw new BusinessException("存在sql注入拦截"); // MyBatis-Plus SQL 注入检测命中时直接阻断。
            }
            boolean hasComma = StrUtil.containsAny(collationFields, "*", "(", ")", ";"); // 禁止排序字段携带函数、通配符和分号。
            if (hasComma) {
                throw new BusinessException("存在sql注入拦截"); // 命中危险字符时直接阻断。
            }
        }
    }

}
