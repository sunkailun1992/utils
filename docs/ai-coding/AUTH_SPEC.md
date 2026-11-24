# 认证规范（Keycloak + OAuth2 资源服务器）

> 涉及登录、访问令牌、接口鉴权、服务间调用身份、令牌交换或 Keycloak 配置时必读。安全通则见 [安全编码规范](SECURITY_CODING_SPEC.md)，Nacos 配置注释遵循 [Nacos 注释与归类规范](NACOS_COMMENT_SPEC.md)。

## 定位

- 第5阶段（2026-11-20/21）起，个人 GitHub 的身份只由 NAS 上的 Keycloak 26.8.2 签发（域 `github-dev`，HTTPS，证书由 mkcert 签发）。域、客户端与作用域由 [Terraform](../../ops/github-dev/keycloak/terraform/README.md) 管理。
- 浏览器经网关 BFF 登录（授权码 + PKCE，会话在网关，前端不持有令牌）；网关把访问令牌转给后端。后端服务都是 OAuth2 资源服务器，只认 Keycloak 签发、受众正确的 RS256 访问令牌。
- 访问令牌只证明身份（声明 `user_id`）。租户、部门、数据权限、权限码、角色与是否停用以 user 服务为准，由资源服务器按请求经 `SecurityContextRpcService` 加载。
- 自签 HS256 令牌（jjwt）、网关透传身份头、HMAC 服务签名与共享密钥都已删除，不留兼容层。
- 依据：RFC 6749/6750（OAuth2 与 Bearer 令牌）、RFC 7636（PKCE）、RFC 8693（令牌交换）、RFC 9068（JWT 访问令牌）、RFC 8725（JWT 最佳实践）、RFC 9700（OAuth 安全最佳实践）、IETF 浏览器应用 BFF 草案、OWASP 身份传播；实现只用 Spring Security 与 Spring Boot 的标准组件和扩展点。

## 资源服务器接入

服务配了 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 就自动接入（`FleetResourceServerAutoConfiguration`）。每个服务在自己的 `<服务>-spring.yaml` 里写成整棵树：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${custom.keycloak-issuer-uri}   # reuse-configuration.yaml 里取 GITHUB_KEYCLOAK_ISSUER_URI
          jws-algorithms: RS256
          audiences: github-api                       # ai-agent 为 github-ai-agent，mcp 为 github-mcp
fleet:
  security:
    oauth2:
      permit-paths: /actuator/health/**,/v3/api-docs/**,/swagger-ui/**,/error   # 写了就整体替换缺省值
      machine-clients: []        # 允许调用本服务的客户端凭证令牌（azp）
      required-azp: []           # 只收这些客户端的令牌；ai-agent 为 github-ai
      context-cache-ttl: 30s     # 业务上下文本地缓存时长
```

- 令牌校验：签发方、受众（`audiences`；没配时只收受众含 `github-api` 的令牌，2.0.6 起，模板仍须显式写出）、有效期（允许 60 秒时钟偏差）、签发时间（2.0.8 起 `FleetIssuedAtValidator`：`iat` 必须有，不能晚于当前时间 60 秒以上；Keycloak 不写 `nbf`，只看 `exp` 时未来签发的令牌能一直用到 iat 加有效期，口径同 RAG 引擎的 PyJWT）、只收 RS256。Spring Security 自带的 `JwtIssuedAtValidator` 是给 DPoP 证明用的双向窗口（签发早于 60 秒也拒），不用于访问令牌。issuer 元数据按需延迟发现，Keycloak 不可达时服务照常启动，受保护接口返回 401，不放行、不打印堆栈，恢复后无需重启。
- 默认过滤链：无状态、关 CSRF（只接 Bearer 令牌）、`permit-paths` 放行，其余要求业务上下文可用的用户或名单里的机器客户端。顺序与 Spring Boot 默认链相同，服务自己的路径专用链要用更小的顺序值；Actuator 仍由 `ActuatorSecurityConfig` 的运维令牌保护，访问令牌不能替代。
- 受保护资源元数据（RFC 9728，2.0.3 起）：Spring Security 7 在默认链上发布 `/.well-known/oauth-protected-resource`（及 `/.well-known/oauth-protected-resource/<资源路径>`），免认证；`resource` 按取元数据的地址推出，`authorization_servers` 为 `issuer-uri`，`bearer_methods_supported` 为 `header`。401 质询带 `resource_metadata`。服务在网关后要让元数据给出网关地址，须开转发头（`server.forward-headers-strategy`）。
- 响应：401（A0220）没带令牌、令牌无效或过期（含签发时间在未来）、签发方不可达；403（A0301）账号未开通（user 里没有该用户或还没关联 Keycloak 用户，2.0.8 起由 401 改 403）、停用、无权访问所请求的租户、登录身份与账号不一致（2.0.8 起，见下）、客户端不在名单、缺少接口权限；503（C0110）user 服务暂时给不出业务上下文（连接类失败先按退避重试，见「租户与业务上下文」）。令牌本身有效时一律 403、不回 401（RFC 6750 §3.1），网关 BFF 不会把它当成会话失效反复重新登录（Keycloak 会话仍在，重新登录拿到的还是同一个用户，以前会形成重定向循环）。都带 RFC 6750 的 `WWW-Authenticate` 头，不回显令牌。
- 身份绑定（2.0.8 起）：OIDC Core §5.7 规定 iss + sub 才是终端用户的稳定标识。提供方在 `SecurityUser.idpSubject` 给出 user 登记的 Keycloak 用户 ID，资源服务器核对它等于令牌的 `sub`，不等即 403「登录身份与账号不一致」并记一行不含令牌与 sub 的警告（防止另建 Keycloak 账号把 `user_id` 属性写成别人的）。提供方没给时（2.0.7 及更早的 user、滚动升级期间的缓存副本）不核对，记警告。
- 启动检查（2.0.8 起）：配了 `issuer-uri` 却既没有本地 `SecurityContextRpcService` 也没有 Dubbo 引用时启动失败，Spring Boot 的启动失败分析给出原因与做法（`FleetSecurityContextProviderFailureAnalyzer`）；以前会照常启动，之后每个用户令牌都被当成用户不存在。只按 Bean 定义查类型，不提前初始化 Dubbo 引用。
- 认证结果：用户令牌为 `FleetAuthenticationToken`（名称是 `user_id`，权限是 user 服务给的权限码与 `ROLE_` 角色，`getUser()` 取 `SecurityUser`）；机器令牌为 `MachineAuthenticationToken`（名称是客户端 ID，权限是 `SCOPE_` 作用域，不写用户与租户上下文）。`@PreAuthorize` 等方法级权限照常可用。
- 上下文：`FleetSecurityContextFilter` 把 `SecurityUser` 写入 `UserContextHolder`（数据权限插件也从这里读）与 `TenantContextHolder`，请求结束清理。

## 租户与业务上下文

- 活动租户只能由请求头 `X-Tenant-Id` 在用户所属租户里选择，缺省为用户的默认租户；是否属于该租户由 user 服务判断（`TENANT_DENIED` 返回 403）。`tenantId`、`tenant-id` 等其它 HTTP 头一律不认，匿名请求不带租户上下文。权限码、角色与数据权限都按活动租户计算。
- 接口参数或请求体里点名的 `tenantId` 不能覆盖活动租户（2.0.7 起）：服务把它写进 `TenantContextHolder` 之前先调 `TenantBoundary.require(tenantId)`，只放行自己的活动租户；平台超级管理员（`ROLE_PLATFORM_SUPER_ADMIN`，管理员分类与租户无关）可以点名任一租户；不满足时 403（A0301），且在任何业务查询之前拒绝。跨租户只给平台超级管理员，租户管理员要管理别的租户，先在 `X-Tenant-Id` 换到该租户（在那里按那个租户的权限计算）。做法同芋道 `TenantSecurityWebFilter`，依据 OWASP API Security Top 10 2023 的 API1。
- 缓存：本地 Caffeine（`context-cache-ttl`，上限 10000）→ Redis（键 `fleet:security:ctx:<userId>:<版本>:<租户或_>`，5 分钟，与 github-dev 域的访问令牌寿命相同；2.0.7 及以前为 30 分钟）→ Dubbo 调 user 服务。只缓存加载成功的结果。
- user 服务改动用户（停用、改权限、改部门或租户）后必须调用 `SecurityUserCache.bumpVersion(userId)`：本实例立刻失效，其他实例最多 `context-cache-ttl` 后失效。Redis 自增失败时版本没变，各实例（含本实例）会继续读到同版本号下的旧副本，最多 Redis 时长加 `context-cache-ttl` 后读到改动，日志写出这个上限。
- 连接类失败重试（2.1.1 起）：缓存未命中、经 Dubbo 向 user 取业务上下文时，由 utils 的 Dubbo 调用端统一重试处理（`UnprocessedRequestFilter` 与 `ConsumerRetryClusterFilter`，对全部同步 Dubbo 调用生效，见 `RPC_API_CODING_SPEC.md`「调用端统一重试」）：请求没发出（Triple 连接断开待重连、提供方暂被移出可用列表等复查）就按指数退避加抖动重试到这次调用的 3 秒超时；user 在提供方把 `resolve` 声明为幂等，请求发出后的连接类失败（对端复位、服务端暂不可用）也最多再试 2 次，并受重试预算约束。超时、业务异常、限流、序列化、鉴权等不重试；仍失败按 503（C0110）返回。转换器本身不再重试（2.1.0 的 `SecurityContextLookupRetry` 已去掉），Dubbo 引用保留 `retries = 0`，与统一重试在每次尝试里的设定一致。
- 提供方：加载成功的结果必须带 `idpSubject`（访问令牌的 `sub`）；还没关联 Keycloak 用户的账号按 `USER_NOT_FOUND` 返回。user 服务实现 `SecurityContextRpcService` 并标 `@Primary`（它只用 `@DubboService` 时由 Dubbo 扫描注册，自动配置看不到，会同时生成一个自引用）。其它服务在 Dubbo 已启用时自动得到 Dubbo 引用，参数与各服务既有引用相同：启动时不初始化（`init = false`，2.0.5 起；Dubbo 默认启动时初始化全部引用，没配注册中心的上下文会起不来），首次调用才初始化并连接，不在启动时检查、Dubbo 自身不重试（重试由调用端统一重试负责）、3 秒超时、跟随灰度路由；初始化失败与调用失败一样返回 503（`FleetSecurityContextReferenceWithoutRegistryTest`、`FleetSecurityContextReferenceFirstCallTest` 约束）。引用由独立的 `FleetSecurityContextDubboAutoConfiguration` 创建（排在 Dubbo 自动配置之后，2.0.4 起）；utils 的自动配置不写成嵌套配置——消费服务默认扫描整个 `com.kellen`，嵌套配置会被提前扫描处理，`@ConditionalOnBean` 提前判定（`AutoConfigurationScanBoundaryTest` 约束）。

## 服务间调用

| 场景 | 授权方式 | 说明 |
|---|---|---|
| 网关 → 后端 | 转发用户访问令牌 | 网关 BFF 持有会话，按 RFC 9700 不轮换机密客户端的刷新令牌 |
| ai → ai-agent | RFC 8693 令牌交换 | 用户令牌换成受众 `github-ai-agent`、`azp=github-ai`、同一 `user_id` 的令牌；ai-agent 用 `required-azp` 只收它 |
| rag → RAG 引擎 | 客户端凭证 | `github-rag`，受众 `github-rag-engine` |
| Prometheus → RAG 引擎 `/metrics` | 客户端凭证 | `github-prometheus`，只用于抓指标 |

服务配了 `spring.security.oauth2.client.registration.*` 就自动接入出站（`FleetOAuth2ClientAutoConfiguration`）：

```yaml
spring:
  security:
    oauth2:
      client:
        provider:
          keycloak:
            token-uri: ${custom.keycloak-issuer-uri}/protocol/openid-connect/token   # 不写 issuer-uri：Boot 会在启动时发现，Keycloak 不可达就起不来
        registration:
          github-ai-agent:
            provider: keycloak
            client-id: github-ai
            client-secret: ${GITHUB_KEYCLOAK_AI_CLIENT_SECRET}
            authorization-grant-type: urn:ietf:params:oauth:grant-type:token-exchange
fleet:
  security:
    oauth2:
      token-exchange:
        github-ai-agent:
          audience: github-ai-agent
      token-endpoint:
        connect-timeout: 5s      # 连接 Keycloak 令牌端点的超时（缺省值）
        read-timeout: 10s        # 等待令牌端点响应的超时（缺省值）
```

- `FleetOAuth2Clients.interceptor(注册ID)` 给 `RestClient`，`webClientFilter(注册ID)` 给 `WebClient`，`bearerToken(注册ID)` 给其他 HTTP 客户端（如 JDK `HttpClient`，自己放进 `Authorization: Bearer`）。客户端凭证以客户端为主体，令牌在所有请求间共享、到期前不重复申请；令牌交换以当前用户为主体、按用户缓存，subject token 以 `access_token` 类型提交（Keycloak 标准令牌交换的要求）。
- 没取到令牌（Keycloak 拒绝、不可达或超时，没有当前用户）一律报错，绝不在不带令牌的情况下调用下游。令牌端点缺省连接 5 秒、读取 10 秒超时（`token-endpoint.*`）：HTTP 客户端库缺省不限读取时间，不设就会在 Keycloak 卡住时一直挂着。
- `WebClient` 的令牌交换从订阅时的安全上下文取当前用户：要在 servlet 请求线程里订阅，或用 `ServletOAuth2AuthorizedClientExchangeFilterFunction.authentication(...)` 显式传入。

## Keycloak 侧约定

- 客户端与受众命名 `github-<服务>`；网关客户端 `github-gateway` 是机密客户端，只开授权码，PKCE S256 必填。
- 用户档案（Keycloak 24 起默认启用声明式用户档案，未声明的属性会被丢弃）声明了 `user_id`，只许管理员查看与修改；资料都选填（user 服务不收集邮箱与姓），首次登录只要求改临时口令。用户名与昵称（`firstName`）以 user 服务为准、由它经 Admin API 写入，用户在账号控制台只能看不能改；域开了「允许改用户名」，管理员改名才能同步。
- `github-api` 作用域给网关的用户令牌加受众 `github-api`、`github-ai` 与 `user_id` 声明；`github-ai` 客户端自带 `user_id` 映射器（不分配 `github-api` 作用域，避免交换后的令牌受众扩大）。

## 禁止事项

- 不自签令牌，不信任任何 HTTP 头传来的身份、角色、权限或租户。
- 不为任何客户端开直接授权（密码模式，RFC 9700 要求不再使用）或隐式模式；测试也走授权码 + PKCE。
- 不把令牌、客户端密钥写进日志、URL 查询串、异常信息、Git、Wiki 或 Nacos 明文。
- 不在没有令牌时调用下游，不把 `jws-algorithms` 放宽到 RS256 以外，不给出站 provider 配 issuer-uri。
- 不在业务服务里另写令牌解析、JWKS 拉取或令牌请求；需要新能力时扩展 utils 的自动配置。

## 测试

- 单元测试：测试夹具 `com.kellen.security.oauth2.test.FakeIssuer`（本机回环随机端口提供 OIDC 发现文档与公钥集、签发 RS256 令牌，2.0.8 起取代 utils 自己的 `TestKeycloak` 与各服务复制的 `FakeIssuer`）配 `MockMvc` 或真实 HTTP，走 Spring Boot 真实的 issuer 发现；业务上下文用 `StubSecurityContextRpcService`。
- 消费者用测试夹具：`testImplementation "com:utils:${utilsVersion}:test-fixtures"`（Gradle `java-test-fixtures`，utils 不发 `.module`，按分类器依赖），不再在服务里复制签发方替身。
- CI 集成测试：`KeycloakContainerIT` 用 Testcontainers 起 Keycloak 26.8.2，导入 `src/test/resources/keycloak/github-dev-realm.json`，验证真实登录、受众拒绝与令牌交换；本机没有 Docker 时跳过。
- 真库测试：`KeycloakLiveTest` 对 NAS 的 `github-dev` 域跑同样三条，只在设置了 `FLEET_KC_TEST_ISSUER` 时运行。测试用户与私有 env 由本机工具 `~/.config/kellen-github/tools/kc_test_user.py ensure` 建立；运行方法：

```bash
( set -a; . ~/.config/kellen-github/phase5/fleet-kc-test.env; set +a; ./gradlew test --tests 'com.kellen.security.oauth2.KeycloakLiveTest' -x :fleet-observability:test )
```
