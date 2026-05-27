package com.kellen.config.web;

import com.kellen.security.config.TenantProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP 请求上下文拦截器。
 *
 * <p>当前拦截器负责从请求头初始化租户上下文，并在请求结束后清理租户上下文。</p>
 *
 * @author 孙凯伦
 */
public class ReqInterceptor implements HandlerInterceptor {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 构造 HTTP 请求上下文拦截器。
     *
     * @param tenantProperties 租户配置属性
     */
    public ReqInterceptor(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供请求头解析使用。
    }

    /**
     * 请求进入 Controller 前初始化上下文。
     *
     * @param request  当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler  处理器对象
     * @return true 表示继续执行请求
     * @throws Exception 拦截器异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        initTenantContext(request); // 从请求头初始化租户上下文。
        response.addHeader("traceId", MDC.get("traceId")); // 将日志 traceId 回写响应头，便于前后端联查日志。
        return HandlerInterceptor.super.preHandle(request, response, handler); // 继续执行后续处理链。
    }

    /**
     * Controller 执行后处理。
     *
     * @param request      当前HTTP请求
     * @param response     当前HTTP响应
     * @param handler      处理器对象
     * @param modelAndView 模型视图
     * @throws Exception 拦截器异常
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView); // 保留 Spring 默认后置处理行为。
    }

    /**
     * 请求完成后清理上下文。
     *
     * @param request  当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler  处理器对象
     * @param ex       请求异常
     * @throws Exception 拦截器异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContextHolder.clear(); // 请求完成后清理租户上下文，避免线程复用串租户。
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex); // 保留 Spring 默认完成处理行为。
    }

    /**
     * 从请求头初始化租户上下文。
     *
     * @param request 当前HTTP请求
     */
    private void initTenantContext(HttpServletRequest request) {
        if (!tenantProperties.isEnabled()) {
            return; // 未开启租户时不读取租户请求头。
        }
        for (String headerName : tenantProperties.getHeaderNames()) {
            String tenantId = request.getHeader(headerName); // 按配置顺序读取租户请求头。
            if (StringUtils.isNotBlank(tenantId)) {
                TenantContextHolder.setTenantId(tenantId); // 找到第一个非空租户ID后写入上下文。
                return;
            }
        }
    }
}
