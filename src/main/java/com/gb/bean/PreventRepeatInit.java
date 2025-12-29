package com.gb.bean;

import com.gb.utils.RedisUtils;
import com.gb.utils.annotations.Methods;
import com.gb.utils.annotations.PreventRepeat;
import com.gb.utils.exception.PreventRepeatException;
import com.gb.utils.methods.MethodsInit;
import com.gb.utils.methods.MethodsParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * 请求接口幂等
 *
 * @author sunkailun
 * @DateTime 2020/12/10  下午5:28
 * @email 376253703@qq.com
 * @explain
 */
@Slf4j
@Component
public class PreventRepeatInit {
    /**
     * redis操作
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public static String getTime(Class entity, String methods) throws Exception {
        //类注解的属性和内容
        List<MethodsParam> list = MethodsInit.init(entity);
        //循环所有属性注解和内容
        for (MethodsParam mp : list) {
            Methods m = mp.getMeta();
            if (m.methods().equals(methods)) {
                return m.methodsName();
            }
        }
        return "";
    }

    /**
     * 幂等拦截
     *
     * @param joinPoint: aop拦截类
     *                   验证码
     * @return void
     * @author sunkailun
     * @DateTime 2020/12/10  下午7:20
     * @email 376253703@qq.com
     */
    public void init(JoinPoint joinPoint, String token) throws Exception {
        //获得执行方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        //判断方法是否加了注解启用
        Boolean preventRepeat = method.isAnnotationPresent(PreventRepeat.class);
        if (preventRepeat != null && preventRepeat) {
            //自定义注解，失效时间
            Integer time = method.getAnnotation(PreventRepeat.class).timeMinutes();
            //日志打印
            log.debug("日志进入：" + joinPoint.getTarget().getClass().toString().split(" ")[1] + "." + joinPoint.getSignature().getName());
            //缓存取出用户
            Map<String, Object> m = RedisUtils.getToken(stringRedisTemplate, token);
            if (Objects.isNull(m)) {
                throw new PreventRepeatException("登陆已失效，请重新登陆");
            }
            Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(m.get("userName") + "-" + joinPoint.getTarget().getClass().toString().split(" ")[1] + "." + joinPoint.getSignature().getName(), token, time, TimeUnit.MINUTES);
            if (Objects.isNull(b) || !b) {
                //抛出异常
                throw new PreventRepeatException(joinPoint.getSignature().getName() + "重复调用");
            }
        }
    }


    /**
     * 幂等拦截删除
     *
     * @param joinPoint: aop拦截类
     *                   验证码
     * @return void
     * @author sunkailun
     * @DateTime 2020/12/10  下午7:21
     * @email 376253703@qq.com
     */
    public void delete(JoinPoint joinPoint, String token) throws Exception {
        //获得执行方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        //判断方法是否加了注解启用
        Boolean preventRepeat = method.isAnnotationPresent(PreventRepeat.class);
        if (preventRepeat != null && preventRepeat) {
            //缓存取出用户
            Map<String, Object> m = RedisUtils.getToken(stringRedisTemplate, token);
            log.debug("日志进入幂等拦截删除,token={}  ,  用户信息={}  ,  joinPoint={}", token, m, joinPoint);
            String s = RedisUtils.get(stringRedisTemplate, m.get("userName") + "-" + joinPoint.getTarget().getClass().toString().split(" ")[1] + "." + joinPoint.getSignature().getName());
            if (StringUtils.isNotBlank(s)) {
                RedisUtils.delete(stringRedisTemplate, m.get("userName") + "-" + joinPoint.getTarget().getClass().toString().split(" ")[1] + "." + joinPoint.getSignature().getName());
            }
        }
    }

}
