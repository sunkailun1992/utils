package com.kellen.aop;

import cn.hutool.core.convert.Convert;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
    private StringRedisTemplate stringRedisTemplate;

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
        //请求值
        Object object = args(point);
        //取出用户
        String token = getToken(request);
        //取出版本号
        String requestVersion = getVersion(request);
        //数据源
        String dataSource = getDataSource(request);
        //判断token不为空
        if (StringUtils.isNotBlank(token)) {
            //接口幂等
            preventRepeatInit.init(point, token);
            //版本号验证
            version(requestVersion);
        }
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
        HttpServletRequest request = getHttpServletRequest();
        Object object = args(joinPoint);
        //取出用户
        String token = getToken(request);
        //判断token不为空
        if (StringUtils.isNotBlank(token)) {
            //后置删除幂等缓存
            preventRepeatInit.delete(joinPoint, token);
        }
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
        //获得请求
        String token = getToken(request);
        //判断token不为空
        if (StringUtils.isNotBlank(token)) {
            //插入日志
            setLog(proceedingJoinPoint, request, token, (end - start), RequestUtil.getParameterMap(request), o, performBefore, performAfter);
        }
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
        HttpServletRequest request = getHttpServletRequest();
        //取出用户
        String token = getToken(request);
        //判断token不为空 幂等异常不删除
        boolean isRepeatEx = e instanceof PreventRepeatException;
        if (e instanceof UndeclaredThrowableException) {
            isRepeatEx = ((UndeclaredThrowableException) e).getUndeclaredThrowable() instanceof PreventRepeatException;
        }
        if (StringUtils.isNotBlank(token) && !isRepeatEx) {
            //异常删除幂等缓存
            preventRepeatInit.delete(joinPoint, token);
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
    private void setLog(ProceedingJoinPoint proceedingJoinPoint, HttpServletRequest httpServletRequest, String token, long time, Map<String, String> map, Object o, String performBefore, String performAfter) {
        Map<String, Object> member = token != null ? JsonUtil.bean(RedisUtils.get(stringRedisTemplate, token), Map.class) : null;
        RequestLog requestLog = new RequestLog();
        if (member != null) {
            requestLog.setUserId(Convert.toLong(member.get("id")));
            requestLog.setUsername(String.valueOf(member.get("userName")));
            requestLog.setName(String.valueOf(member.get("name")));
            requestLog.setUrl(httpServletRequest.getRequestURI());
            requestLog.setElapsedTime(time);
            requestLog.setRequest(map);
            requestLog.setResults(o);
            requestLog.setSystemName(applicationContext.getEnvironment().getProperty("swagger.name") + "服务");
            requestLog.setCreateName(String.valueOf(member.get("name")));
            requestLog.setCreateDateTime(LocalDateTime.now());
            requestLog.setIp(String.valueOf(member.get("ip")));
            requestLog.setPerformBefore(performBefore);
            requestLog.setPerformAfter(performAfter);
            requestLog.setEnvironment("环境：" + applicationContext.getEnvironment().getProperty("spring.profiles.active") + "，数据源：" + DynamicSourceTtl.get() != null ? DynamicSourceTtl.get() : httpServletRequest.getHeader("dataSource"));
            try {
                requestLog.setDescription(MethodsJudge.description(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName(), map));
                requestLog.setInterfaceName(MethodsJudge.getInterfaceName(Class.forName(proceedingJoinPoint.getTarget().getClass().getName()), proceedingJoinPoint.getSignature().getName()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            /**
             * mongodb新增
             */
            requestLogService.insert(requestLog);
            /**
             * elasticsearch新增
             */
            ElasticSearchRequestLog elasticSearchRequestLog = GeneralConvertor.convertor(requestLog, ElasticSearchRequestLog.class);
            elasticSearchRequestLog.setId(IdUtil.simpleUUID());
            elasticSearchRequestLog.setCreateDateTime(LocalDateTime.now());
            elasticSearchRequestLogService.insert(elasticSearchRequestLog);
            /**
             * 判断超时发送钉钉
             */
            dingDing(proceedingJoinPoint, httpServletRequest, time, map, o);
        }
    }

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

    private Object args(JoinPoint joinPoint) {
        Object object = null;
        //循环取出对象,排除基础类型
        for (Object obj : joinPoint.getArgs()) {
            if (obj != null) {
                if (ObjectUtils.isBaseType(obj.getClass())) {
                    object = obj;
                }
            }
        }
        return object;
    }


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
     * @name: getToken
     * @description: TODO 获得token
     * @return: java.lang.String
     * @date: 2021/4/1 2:08 下午
     */
    private String getToken(HttpServletRequest request) {
        //取出用户
        String token = null;
        //判断如果有授权就直接取，否则就从集合中取出
        if (request.getHeader("token") != null) {
            if (!request.getHeader("token").equals("eaa1929451cd43efb3f4668eed25e3f9")) {
                token = request.getHeader("token");
            }
        }
        return token;
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

}
