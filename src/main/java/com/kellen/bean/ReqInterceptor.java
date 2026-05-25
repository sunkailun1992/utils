package com.kellen.bean;

import com.kellen.utils.TenantContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ReqInterceptor implements HandlerInterceptor {

    private final TenantProperties tenantProperties;

    public ReqInterceptor(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        initTenantContext(request);
        response.addHeader("traceId", MDC.get("traceId"));
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantContextHolder.clear();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    private void initTenantContext(HttpServletRequest request) {
        if (!tenantProperties.isEnabled()) {
            return;
        }
        for (String headerName : tenantProperties.getHeaderNames()) {
            String tenantId = request.getHeader(headerName);
            if (StringUtils.isNotBlank(tenantId)) {
                TenantContextHolder.setTenantId(tenantId);
                return;
            }
        }
    }
}
