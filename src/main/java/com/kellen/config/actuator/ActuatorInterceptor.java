package com.kellen.config.actuator;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 已停用的历史 MVC 拦截器；Actuator 实际保护由独立 SecurityFilterChain 负责。
 *
 * <p>保留类型供旧消费者编译，误注册时一律拒绝，不再携带或接受历史固定口令。
 * MVC 拦截器不会覆盖 Actuator 自己的 HandlerMapping，不能将它作为管理端点保护措施。</p>
 */
@Deprecated(since = "1.4.9", forRemoval = true)
public class ActuatorInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        return false;
    }
}
