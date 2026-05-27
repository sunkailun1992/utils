package com.kellen.aop;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.toolkit.sql.SqlInjectionUtils;
import com.kellen.aliyun.dingding.DingDingUtil;
import com.kellen.bean.PreventRepeatInit;
import com.kellen.log.entity.ElasticSearchRequestLog;
import com.kellen.log.entity.RequestLog;
import com.kellen.log.service.ElasticSearchRequestLogService;
import com.kellen.log.service.RequestLogService;
import com.kellen.security.SecurityUser; // 使用 Spring Security 解析后的当前用户作为日志用户来源。
import com.kellen.security.UserContextHolder; // 从统一用户上下文读取用户信息，替代历史 token Redis 用户读取。
import com.kellen.utils.*;
import com.kellen.utils.exception.BusinessException;
import com.kellen.utils.exception.PreventRepeatException;
import com.kellen.utils.methods.MethodsJudge;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.UndeclaredThrowableException;
import java.time.LocalDateTime;
import java.util.Arrays;
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
public class AopAspectJ {

    /**
     * 防重复提交处理器。
     */
    @Autowired
    private PreventRepeatInit preventRepeatInit;

    /**
     * Mongo 请求日志服务。
     */
    @Autowired
    private RequestLogService requestLogService;

    /**
     * Elasticsearch 请求日志服务。
     */
    @Autowired
    private ElasticSearchRequestLogService elasticSearchRequestLogService;

    /**
     * Spring 应用上下文。
     */
    @Autowired
    private ConfigurableApplicationContext applicationContext;

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
     * @DateTime 2019/5/6  4:13 PM
     * @email 376253703@qq.com
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
     * @DateTime 2018/7/16  下午3:04
     * @email 376253703@qq.com
     */
    private void setLog(ProceedingJoinPoint proceedingJoinPoint, HttpServletRequest httpServletRequest, long time, Map<String, String> map, Object o, String performBefore, String performAfter) {
        SecurityUser user = UserContextHolder.get(); // 从 Spring Security 过滤器写入的上下文获取当前用户。
        RequestLog requestLog = new RequestLog(); // 创建请求日志实体，后续统一补齐请求、响应、用户和环境信息。
        if (user != null) {
            requestLog.setUserId(parseUserId(user.getUserId())); // 兼容日志表 Long 类型用户 ID，非数字用户 ID 记录为空。
            requestLog.setUsername(user.getUsername()); // 记录当前认证用户名。
            requestLog.setName(user.getUsername()); // 历史日志 name 字段沿用用户名填充。
            requestLog.setCreateName(user.getUsername()); // 日志创建人使用当前认证用户名。
        }
        requestLog.setUrl(httpServletRequest.getRequestURI()); // 记录当前请求 URI。
        requestLog.setElapsedTime(time); // 记录接口耗时。
        requestLog.setRequest(map); // 记录请求参数。
        requestLog.setResults(o); // 记录接口返回结果。
        requestLog.setSystemName(applicationContext.getEnvironment().getProperty("swagger.name") + "服务"); // 记录服务名称。
        requestLog.setCreateDateTime(LocalDateTime.now()); // 记录日志创建时间。
        requestLog.setIp(IpUtils.getIp(httpServletRequest)); // 记录客户端真实 IP。
        requestLog.setPerformBefore(performBefore); // 记录 Methods 扩展点执行前结果。
        requestLog.setPerformAfter(performAfter); // 记录 Methods 扩展点执行后结果。
        requestLog.setEnvironment("环境：" + applicationContext.getEnvironment().getProperty("spring.profiles.active") + "，数据源：" + (DynamicSourceTtl.get() != null ? DynamicSourceTtl.get() : httpServletRequest.getHeader("dataSource"))); // 记录当前环境和数据源。
        try {
            requestLog.setDescription(MethodsJudge.description(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName(), map)); // 记录接口业务描述。
            requestLog.setInterfaceName(MethodsJudge.getInterfaceName(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName())); // 记录接口展示名称。
        } catch (Exception e) {
            log.warn("请求日志描述解析失败，uri: {}", httpServletRequest.getRequestURI(), e); // 描述解析失败不阻断业务响应。
        }
        requestLogService.insert(requestLog); // 写入 Mongo 请求日志。
        ElasticSearchRequestLog elasticSearchRequestLog = GeneralConvertor.convertor(requestLog, ElasticSearchRequestLog.class); // 将 Mongo 日志实体转换为 ES 日志实体。
        elasticSearchRequestLog.setId(IdUtil.simpleUUID()); // 为 ES 日志生成独立 ID。
        elasticSearchRequestLog.setCreateDateTime(LocalDateTime.now()); // 设置 ES 日志创建时间。
        elasticSearchRequestLogService.insert(elasticSearchRequestLog); // 写入 ES 请求日志。
        dingDing(proceedingJoinPoint, httpServletRequest, time, map, o); // 保留慢请求钉钉提醒。
    }

    /**
     * 请求耗时超限钉钉提醒
     *
     * @param proceedingJoinPoint: aop拦截类
     * @param httpServletRequest:  当前请求
     * @param time:                接口耗时
     * @param map:                 请求参数
     * @param o:                   返回结果
     * @return void
     * @author 孙凯伦
     * @DateTime 2018/7/16  下午3:04
     * @email 376253703@qq.com
     */
    @Async
    public void dingDing(ProceedingJoinPoint proceedingJoinPoint, HttpServletRequest httpServletRequest, long time, Map<String, String> map, Object o) {
        if (time >= 3000) {
            try {
                String json = DingDingUtil.markdown(false, "请求超长",
                        " #### 请求模块：" + applicationContext.getEnvironment().getProperty("swagger.name") + "服务" + "\n "
                                + "#### 请求名称：" + MethodsJudge.getInterfaceName(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName()) + "\n "
                                + "#### 请求地址：" + httpServletRequest.getRequestURI() + "\n "
                                + "#### traceId：" + MDC.get("traceId") + "\n "
                                + "#### 消耗时间：" + (time % (1000 * 60)) / 1000 + "/秒\n "
                                + "\n---\n"
                                + "#### 请求参数：\n```\n" + JSON.toJSONString(JSONObject.parseObject(JsonUtil.json(map)), SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteDateUseDateFormat) + "\n```\n"
                        , Arrays.asList(""));
                DingDingUtil.sendReboot(json);
            } catch (Exception e) {
                log.warn("慢请求钉钉提醒发送失败，uri: {}", httpServletRequest.getRequestURI(), e); // 钉钉发送失败不影响业务响应。
            }
        }
    }


    /**
     * 获取当前请求对象
     *
     * @param :
     * @return jakarta.servlet.http.HttpServletRequest
     * @author 孙凯伦
     * @DateTime 2019/5/6  10:36 AM
     * @email 376253703@qq.com
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
            log.debug("aop获取到的dataSource {}", requestDataSource); // 输出数据源调试日志。
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

    /**
     * 解析日志用户ID
     *
     * @param userId: Spring Security上下文中的用户ID
     * @return java.lang.Long
     * @author 孙凯伦
     * @DateTime 2026/5/26  下午
     * @email 376253703@qq.com
     */
    private Long parseUserId(String userId) {
        if (!StringUtils.isNumeric(userId)) { // userId 可能是 u_admin_100 这类业务字符串，不能强转 Long。
            return null; // 非数字用户 ID 不写入 Long 类型日志字段。
        }
        return Long.valueOf(userId); // 数字用户 ID 按历史日志字段类型写入。
    }

}
