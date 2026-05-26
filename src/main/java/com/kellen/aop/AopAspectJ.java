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
import com.kellen.utils.exception.VersionException;
import com.kellen.utils.methods.MethodsJudge;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * Created with IntelliJ IDEA
 *
 * @author sunkailun
 * @DateTime 2019/5/6  10:36 AM
 * @email 376253703@qq.com
 * @explain
 */
@Slf4j
@Aspect
@Order(1)
@Component
public class AopAspectJ {

    @Autowired
    private PreventRepeatInit preventRepeatInit;

    @Autowired
    private RequestLogService requestLogService;

    @Autowired
    private ElasticSearchRequestLogService elasticSearchRequestLogService;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Value("${version}")
    private String version;

    @Value("${compatibleVersion}")
    private String compatibleVersion;


    /**
     * 对所有LoginRequired的注解类实现切点
     *
     * @param :
     * @return void
     * @author sunkailun
     * @DateTime 2019/5/6  5:07 PM
     * @email 376253703@qq.com
     */
    @Pointcut("within(@com.kellen.utils.annotations.RequestRequired *)")
    public void pointcut() {

    }

    /**
     * 前置增强
     *
     * @param point:
     * @return void
     * @author sunkailun
     * @DateTime 2019/5/6  4:29 PM
     * @email 376253703@qq.com
     */
    @Before("pointcut()")
    public void before(JoinPoint point) throws Exception {
        //请求头
        HttpServletRequest request = getHttpServletRequest();
        //校验
        verify(request);
        //取出版本号
        String requestVersion = getVersion(request);
        //数据源
        String dataSource = getDataSource(request);
        //接口幂等
        preventRepeatInit.init(point); // 保留防重复提交能力，但内部不再依赖旧 token。
        //版本号验证
        version(requestVersion); // 保留版本校验能力，仍使用请求头 version 与服务配置比对。
        //配置使用数据源
        DynamicSourceTtl.push(dataSource);
    }


    /**
     * 后置增强
     *
     * @param :
     * @return void
     * @author sunkailun
     * @DateTime 2019/5/6  4:13 PM
     * @email 376253703@qq.com
     */
    @After("pointcut()")
    public void after(JoinPoint joinPoint) throws Exception {
        //后置删除幂等缓存
        preventRepeatInit.delete(joinPoint); // 方法正常结束后释放本次 @PreventRepeat 生成的幂等锁。
        //移除数据源变量，防止堆积
        DynamicSourceTtl.clear();
    }

    /**
     * 环绕增强
     *
     * @param proceedingJoinPoint:
     * @return void
     * @author sunkailun
     * @DateTime 2019/5/6  5:02 PM
     * @email 376253703@qq.com
     */
    @Around("pointcut()")
    public Object around(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        HttpServletRequest request = getHttpServletRequest();
        //开始执行时间
        long start = System.currentTimeMillis();
        //执行前
        String performBefore = MethodsJudge.performBefore(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName(), RequestUtil.getParameterMap(request));
        Object o = proceedingJoinPoint.proceed();
        //结束执行时间
        long end = System.currentTimeMillis();
        //执行后
        String performAfter = MethodsJudge.performAfter(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName(), RequestUtil.getParameterMap(request));
        log.debug("耗时:" + (end - start));
        //插入日志
        setLog(proceedingJoinPoint, request, (end - start), RequestUtil.getParameterMap(request), o, performBefore, performAfter); // 日志落库不再要求旧 token 存在。
        return o;
    }


    /**
     * 异常增强
     *
     * @param :
     * @return void
     * @author sunkailun
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
     * @author sunkailun
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
            e.printStackTrace(); // 保持历史行为，描述解析失败不阻断业务响应。
        }
        /**
         * mongodb新增
         */
        requestLogService.insert(requestLog); // 写入 Mongo 请求日志。
        /**
         * elasticsearch新增
         */
        ElasticSearchRequestLog elasticSearchRequestLog = GeneralConvertor.convertor(requestLog, ElasticSearchRequestLog.class); // 将 Mongo 日志实体转换为 ES 日志实体。
        elasticSearchRequestLog.setId(IdUtil.simpleUUID()); // 为 ES 日志生成独立 ID。
        elasticSearchRequestLog.setCreateDateTime(LocalDateTime.now()); // 设置 ES 日志创建时间。
        elasticSearchRequestLogService.insert(elasticSearchRequestLog); // 写入 ES 请求日志。
        /**
         * 判断超时发送钉钉
         */
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
     * @author sunkailun
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
                e.printStackTrace();
            }
        }
    }


    /**
     * 获取当前请求对象
     *
     * @param :
     * @return jakarta.servlet.http.HttpServletRequest
     * @author sunkailun
     * @DateTime 2019/5/6  10:36 AM
     * @email 376253703@qq.com
     */
    private HttpServletRequest getHttpServletRequest() {
        //获得请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes.getRequest();
    }


    /**
     * @param requestVersion
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: version
     * @description: TODO 版本校验
     * @return: void
     * @date: 2021/4/1 2:08 下午
     */
    private void version(String requestVersion) throws VersionException {
        //版本号判断
        if (StringUtils.isNotBlank(requestVersion)) {
            //判断请求版本参数是否正常
            if (requestVersion.split("\\.").length == 3) {
                //系统版本号
                //String s = version.split("\\.")[0] + "." + version.split("\\.")[1];
                String s = version;
                //请求版本号
                //String r = requestVersion.split("\\.")[0] + "." + requestVersion.split("\\.")[1];
                String r = requestVersion;
                //判断大版本是否一致
                if (!s.equals(r)) {
                    //是否成功
                    Boolean b = true;
                    //判断是否版本不一致，是否兼容版本
                    String[] xx = compatibleVersion.split(",");
                    //循环兼容版本
                    for (String x : xx) {
                        if (x.equals(r)) {
                            b = false;
                        }
                    }
                    //判断未找到兼容版
                    if (b) {
                        //抛出异常
                        throw new VersionException("系统版本：" + version + "与请求版本:" + requestVersion + "不一致");
                    }
                }
            } else {
                //抛出异常
                throw new VersionException("请求版本参数异常");
            }
        }
    }


    /**
     * @param request
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: getVersion
     * @description: TODO  获得版本
     * @return: java.lang.String
     * @date: 2021/4/1 2:09 下午
     */
    private String getVersion(HttpServletRequest request) {
        //取出用户
        String version = null;
        //判断如果有授权就直接取，否则就从集合中取出
        if (request.getHeader("version") != null) {
            version = request.getHeader("version");
        }
        return version;
    }


    /**
     * @param request
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: dataSource
     * @description: TODO  数据源获取
     * @return: java.lang.String
     * @date: 2021/6/29 1:42 下午
     */
    private String getDataSource(HttpServletRequest request) throws Exception {
        //取出用户
        String dataSource = "master";
        String requestDataSource = request.getHeader("dataSource");
        //判断如果有授权就直接取，否则就从集合中取出
        if (StringUtils.isNotBlank(requestDataSource)) {
            log.debug("aop获取到的dataSource {}", requestDataSource);
            dataSource = requestDataSource;
        }
        return dataSource;
    }


    /**
     * TODO 参数校验
     *
     * @param request
     * @return void
     * @author 孙凯伦
     * @methodName verify
     * @time 2023/10/12 09:44
     */
    private static void verify(HttpServletRequest request) {
        String collationFields = request.getParameter("collationFields");
        if (StringUtils.isNotBlank(collationFields)) {
            if (SqlInjectionUtils.check(collationFields)) {
                throw new BusinessException("存在sql注入拦截");
            }
            boolean hasComma = StrUtil.containsAny(collationFields, "*", "(", ")", ";");
            if (hasComma) {
                throw new BusinessException("存在sql注入拦截");
            }
        }
    }

    /**
     * 解析日志用户ID
     *
     * @param userId: Spring Security上下文中的用户ID
     * @return java.lang.Long
     * @author sunkailun
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
