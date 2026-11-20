package com.kellen.security.oauth2;

import com.kellen.utils.enumeration.ReturnCode;
import com.kellen.utils.json.JsonUtil;
import com.kellen.utils.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 资源服务器的 401、403、503 统一响应：状态码与 {@code WWW-Authenticate} 头沿用 Spring Security 的
 * Bearer 令牌实现（RFC 6750），响应体换成统一的 {@link ApiResponse}。不回显令牌，服务端错误只记一行警告、不打印堆栈。
 *
 * <ul>
 *     <li>401（A0220）：没带令牌、令牌无效或过期、令牌用户不存在、签发方暂不可达（拒绝访问，不放行）。</li>
 *     <li>403（A0301）：账号停用、无权访问所请求的租户、客户端不在允许名单、缺少接口所需权限。</li>
 *     <li>503（C0110）：令牌有效但 user 服务暂时给不出业务上下文。</li>
 * </ul>
 *
 * @author 孙凯伦
 */
class FleetSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(FleetSecurityErrorHandler.class);

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    private final BearerTokenAccessDeniedHandler bearerAccessDenied = new BearerTokenAccessDeniedHandler();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof FleetSecurityContextUnavailableException) {
            log.warn("用户安全上下文暂不可用，返回 503：{} {}，{}", request.getMethod(), request.getRequestURI(), exception.getMessage());
            write(response, HttpStatus.SERVICE_UNAVAILABLE, ApiResponse.fail(ReturnCode.RPC_SERVICE_ERROR, "用户安全上下文暂不可用，请稍后重试"));
            return;
        }
        if (exception instanceof AuthenticationServiceException) {
            log.warn("访问令牌暂时无法校验，拒绝访问：{} {}，{}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        }
        bearerEntryPoint.commence(request, response, exception);
        write(response, HttpStatus.UNAUTHORIZED, ApiResponse.fail(ReturnCode.USER_AUTHENTICATION_FAILED));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        bearerAccessDenied.handle(request, response, exception);
        write(response, HttpStatus.FORBIDDEN, ApiResponse.fail(ReturnCode.ACCESS_UNAUTHORIZED, deniedReason()));
    }

    private static String deniedReason() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof FleetAuthenticationToken token) {
            return switch (token.getStatus()) {
                case DISABLED -> "账号已停用";
                case TENANT_DENIED -> "无权访问所请求的租户";
                default -> null;
            };
        }
        if (authentication instanceof MachineAuthenticationToken) {
            return null;
        }
        return authentication != null && authentication.isAuthenticated() ? "客户端无权访问本服务" : null;
    }

    private static void write(HttpServletResponse response, HttpStatus status, ApiResponse<?> body) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtil.toJsonStr(body));
    }
}
