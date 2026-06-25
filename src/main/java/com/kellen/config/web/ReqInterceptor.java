package com.kellen.config.web;

import com.kellen.security.config.TenantProperties;
import com.kellen.traffic.TrafficGovernanceContext;
import com.kellen.traffic.TrafficGovernanceProperties;
import com.kellen.utils.context.TenantContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * HTTP 请求上下文拦截器。
 *
 * <p>当前拦截器负责从请求头初始化租户和流量治理上下文，并在请求结束后清理。</p>
 *
 * @author 孙凯伦
 */
public class ReqInterceptor implements HandlerInterceptor {

    /**
     * 租户配置属性。
     */
    private final TenantProperties tenantProperties;

    /**
     * 流量治理配置属性。
     */
    private final TrafficGovernanceProperties trafficGovernanceProperties;

    /**
     * 构造 HTTP 请求上下文拦截器。
     *
     * @param tenantProperties             租户配置属性
     * @param trafficGovernanceProperties 流量治理配置属性
     */
    public ReqInterceptor(TenantProperties tenantProperties, TrafficGovernanceProperties trafficGovernanceProperties) {
        this.tenantProperties = tenantProperties; // 保存租户配置，供请求头解析使用。
        this.trafficGovernanceProperties = trafficGovernanceProperties; // 保存流量治理配置，供请求头解析使用。
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
        initTrafficGovernanceContext(request); // 从请求头初始化发布版本、泳道和灰度 tag 上下文。
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
        TrafficGovernanceContext.clear(); // 请求完成后清理流量治理上下文，避免线程复用串版本或泳道。
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

    /**
     * 从请求头初始化流量治理上下文。
     *
     * @param request 当前HTTP请求
     */
    private void initTrafficGovernanceContext(HttpServletRequest request) {
        if (trafficGovernanceProperties == null || !trafficGovernanceProperties.isEnabled()) {
            return; // 未开启流量治理时不读取治理请求头。
        }
        TrafficGovernanceProperties.Request config = trafficGovernanceProperties.getRequest();
        String releaseVersion = sanitize(firstNotBlank(request.getHeader(config.getReleaseVersionHeader()), config.getDefaultReleaseVersion()), config);
        String lane = sanitize(firstNotBlank(request.getHeader(config.getLaneHeader()), config.getDefaultLane()), config);
        String canaryTag = sanitize(request.getHeader(config.getCanaryTagHeader()), config);
        if (StringUtils.isBlank(canaryTag) && config.isTagFallbackToReleaseVersion()) {
            canaryTag = releaseVersion; // 需要按发布版本强制切流时，把版本作为 Dubbo tag 使用。
        }
        Integer canaryWeight = null;
        if (config.isAllowClientWeightHeader()) {
            canaryWeight = parseWeight(request.getHeader(config.getCanaryWeightHeader()));
        }
        TrafficGovernanceContext.set(new TrafficGovernanceContext.Snapshot(releaseVersion, lane, canaryTag, canaryWeight));
    }

    /**
     * 过滤治理字段，避免异常值进入后续 RPC。
     *
     * @param value  原始值
     * @param config 请求侧治理配置
     * @return 合法值，非法时返回 null
     */
    private String sanitize(String value, TrafficGovernanceProperties.Request config) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Pattern.matches(config.getAllowedValuePattern(), normalized) ? normalized : null;
        } catch (PatternSyntaxException ex) {
            return null; // 配置错误时保守丢弃治理字段，不把未校验值透传到 RPC。
        }
    }

    /**
     * 解析受控权重。
     *
     * @param raw 原始权重
     * @return 0 到 100 之间的权重，非法时返回 null
     */
    private Integer parseWeight(String raw) {
        String normalized = StringUtils.trimToNull(raw);
        if (normalized == null || !StringUtils.isNumeric(normalized)) {
            return null;
        }
        int weight = Integer.parseInt(normalized);
        return weight >= 0 && weight <= 100 ? weight : null;
    }

    /**
     * 返回第一个非空字符串。
     *
     * @param first  第一个候选值
     * @param second 第二个候选值
     * @return 第一个非空字符串
     */
    private String firstNotBlank(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }
}
