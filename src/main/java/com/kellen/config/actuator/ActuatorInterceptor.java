package com.kellen.config.actuator;

import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actuator 端点访问拦截器。
 *
 * <p>对 {@code /actuator} 路径校验请求头令牌，缺失或不匹配时返回 401，防止运维端点对外暴露。
 * 令牌应外置到配置，不应硬编码在代码中。</p>
 *
 * @author 孙凯伦
 */
public class ActuatorInterceptor implements HandlerInterceptor {

    private static final Pattern actuatorReqPattern = Pattern.compile("/actuator$|/actuator/", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        Matcher matcher = actuatorReqPattern.matcher(requestURI);
        boolean match = matcher.find();
        if (match) {
            String actuatorReq = request.getHeader("actuatorReq");
            if (!"e70d96bc080044c5840b85ab986832ae".equals(actuatorReq)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                return false;
            }
        }
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
