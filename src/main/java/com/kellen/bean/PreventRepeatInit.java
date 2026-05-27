package com.kellen.bean;

import com.kellen.security.SecurityUser; // 使用当前认证用户生成幂等 key。
import com.kellen.security.UserContextHolder; // 从 Spring Security 用户上下文读取用户信息，替代旧 token。
import com.kellen.utils.TenantContextHolder; // 使用当前租户生成幂等 key，避免跨租户互相影响。
import com.kellen.utils.annotations.Methods;
import com.kellen.utils.annotations.PreventRepeat;
import com.kellen.utils.exception.PreventRepeatException;
import com.kellen.utils.methods.MethodsInit;
import com.kellen.utils.methods.MethodsParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * 请求接口幂等
 *
 * @author 孙凯伦
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
     * @author 孙凯伦
     * @DateTime 2020/12/10  下午7:20
     * @email 376253703@qq.com
     */
    public void init(JoinPoint joinPoint) throws Exception {
        //获得执行方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 获取当前被拦截的方法签名。
        Method method = signature.getMethod(); // 获取当前被拦截的方法对象。
        //判断方法是否加了注解启用
        Boolean preventRepeat = method.isAnnotationPresent(PreventRepeat.class); // 仅处理标记了 @PreventRepeat 的方法。
        if (preventRepeat != null && preventRepeat) {
            //自定义注解，失效时间
            Integer time = method.getAnnotation(PreventRepeat.class).timeMinutes(); // 读取注解配置的幂等锁过期分钟数。
            //日志打印
            log.debug("日志进入：" + joinPoint.getTarget().getClass().toString().split(" ")[1] + "." + joinPoint.getSignature().getName());
            Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(getRepeatKey(joinPoint), "1", time, TimeUnit.MINUTES); // 使用租户、用户和方法维度写入 Redis 幂等锁。
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
     * @author 孙凯伦
     * @DateTime 2020/12/10  下午7:21
     * @email 376253703@qq.com
     */
    public void delete(JoinPoint joinPoint) throws Exception {
        //获得执行方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature(); // 获取当前被拦截的方法签名。
        Method method = signature.getMethod(); // 获取当前被拦截的方法对象。
        //判断方法是否加了注解启用
        Boolean preventRepeat = method.isAnnotationPresent(PreventRepeat.class); // 仅清理标记了 @PreventRepeat 的方法锁。
        if (preventRepeat != null && preventRepeat) {
            String repeatKey = getRepeatKey(joinPoint); // 复用进入方法时的 key 规则定位 Redis 锁。
            log.debug("日志进入幂等拦截删除,key={}  ,  joinPoint={}", repeatKey, joinPoint); // 打印幂等锁清理日志。
            String s = stringRedisTemplate.opsForValue().get(repeatKey); // 查询 Redis 中是否仍存在该幂等锁。
            if (StringUtils.isNotBlank(s)) {
                stringRedisTemplate.delete(repeatKey); // 方法结束后释放幂等锁，允许后续正常请求。
            }
        }
    }

    /**
     * 获取幂等锁缓存Key
     *
     * @param joinPoint: aop拦截类
     * @return java.lang.String
     * @author 孙凯伦
     * @DateTime 2026/5/26  下午
     * @email 376253703@qq.com
     */
    private String getRepeatKey(JoinPoint joinPoint) {
        SecurityUser user = UserContextHolder.get(); // 获取当前认证用户，替代历史 Redis token 用户查询。
        String tenantId = StringUtils.defaultIfBlank(TenantContextHolder.getTenantId(), "default"); // 缺少租户时使用 default，保证 key 结构稳定。
        String userFlag = user == null ? "anonymous" : StringUtils.defaultIfBlank(user.getUserId(), user.getUsername()); // 优先使用用户 ID，没有则回退用户名或匿名标识。
        return "prevent-repeat:" + tenantId + ":" + userFlag + ":"
                + joinPoint.getTarget().getClass().getName() + "." + joinPoint.getSignature().getName(); // key 粒度为租户、用户、类名和方法名。
    }

}
