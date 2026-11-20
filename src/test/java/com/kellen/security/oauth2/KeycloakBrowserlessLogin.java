package com.kellen.security.oauth2;

import com.kellen.utils.json.JsonUtil;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 不开浏览器完成 Keycloak 的授权码加 PKCE 登录：取登录页、提交用户名口令表单、从回调地址取授权码、用机密客户端换令牌。
 *
 * <p>走的是网关 BFF 正式用的客户端与流程，不为测试开直接授权（密码模式，RFC 9700 要求不再使用）。
 * 只认 Keycloak 默认登录主题（表单 id 为 kc-form-login）。登录页种下的会话 Cookie 显式带回：Keycloak 给它们加了 Secure，
 * 浏览器对 http://localhost 照样发送（安全上下文），JDK 的 CookieManager 对非 https 地址不返回，容器测试会因此失败。</p>
 */
public final class KeycloakBrowserlessLogin {

    private static final Pattern LOGIN_FORM = Pattern.compile("<form\\b[^>]*\\bid=\"kc-form-login\"[^>]*>");

    private static final Pattern ACTION = Pattern.compile("\\baction=\"([^\"]+)\"");

    private KeycloakBrowserlessLogin() {
    }

    /**
     * 登录并取访问令牌。
     *
     * @param issuer       签发方（域地址）
     * @param clientId     机密客户端 ID
     * @param clientSecret 机密客户端密钥
     * @param redirectUri  客户端登记的回调地址（不需要真有服务在监听）
     * @param username     用户名
     * @param password     口令
     * @return 访问令牌
     * @throws Exception 登录或换令牌失败
     */
    public static String accessToken(String issuer, String clientId, String clientSecret, String redirectUri,
                                     String username, String password) throws Exception {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
        String verifier = randomUrlSafe(48);
        String state = UUID.randomUUID().toString();
        Map<String, String> authorize = new LinkedHashMap<>();
        authorize.put("client_id", clientId);
        authorize.put("redirect_uri", redirectUri);
        authorize.put("response_type", "code");
        authorize.put("scope", "openid");
        authorize.put("state", state);
        authorize.put("code_challenge", challenge(verifier));
        authorize.put("code_challenge_method", "S256");
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/auth?" + form(authorize)))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (page.statusCode() != 200) {
            throw new IllegalStateException("登录页返回 " + page.statusCode());
        }
        String action = loginAction(page.body());
        HttpResponse<String> submitted = http.send(HttpRequest.newBuilder(URI.create(action)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Cookie", cookies(page))
                .POST(HttpRequest.BodyPublishers.ofString(form(Map.of("username", username, "password", password, "credentialId", ""))))
                .build(), HttpResponse.BodyHandlers.ofString());
        String location = submitted.headers().firstValue("Location").orElse("");
        if (submitted.statusCode() != 302 || !location.startsWith(redirectUri)) {
            throw new IllegalStateException("登录没有回到回调地址，状态 " + submitted.statusCode());
        }
        Map<String, String> callback = parseQuery(URI.create(location).getRawQuery());
        if (!state.equals(callback.get("state")) || callback.get("code") == null) {
            throw new IllegalStateException("回调缺少授权码或 state 不一致");
        }
        Map<String, String> exchange = new LinkedHashMap<>();
        exchange.put("grant_type", "authorization_code");
        exchange.put("code", callback.get("code"));
        exchange.put("redirect_uri", redirectUri);
        exchange.put("code_verifier", verifier);
        HttpResponse<String> token = http.send(HttpRequest.newBuilder(URI.create(issuer + "/protocol/openid-connect/token"))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.ofString(form(exchange))).build(), HttpResponse.BodyHandlers.ofString());
        if (token.statusCode() != 200) {
            throw new IllegalStateException("换令牌返回 " + token.statusCode());
        }
        Object accessToken = JsonUtil.toBean(token.body(), Map.class).get("access_token");
        return String.valueOf(accessToken);
    }

    /**
     * 解出令牌的声明（只为断言，不校验签名；签名由资源服务器校验）。
     *
     * @param token JWT
     * @return 声明
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> claims(String token) {
        String payload = token.split("\\.")[1];
        return JsonUtil.toBean(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8), Map.class);
    }

    private static String cookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(header -> header.split(";", 2)[0].trim())
                .filter(pair -> pair.contains("=") && !pair.endsWith("="))
                .collect(Collectors.joining("; "));
    }

    private static String loginAction(String html) {
        Matcher form = LOGIN_FORM.matcher(html);
        if (!form.find()) {
            throw new IllegalStateException("登录页里没有 kc-form-login 表单");
        }
        Matcher action = ACTION.matcher(form.group());
        if (!action.find()) {
            throw new IllegalStateException("登录表单没有 action");
        }
        return action.group(1).replace("&amp;", "&");
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static String randomUrlSafe(int bytes) {
        byte[] random = new byte[bytes];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new LinkedHashMap<>();
        if (query == null) {
            return values;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8), parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }
}
