# RPC API 协作规范

## 契约归属

- 跨服务 Dubbo RPC 接口、DTO、枚举和值对象统一维护在同级 `../rpc-api`。
- `utils` 不依赖 `rpc-api`，也不维护业务 RPC 接口、DTO 或 provider/consumer 代码。
- `utils` 只提供 Dubbo 上下文透传、公共配置、公共工具和中间件适配。

## 当前项目角色

- `utils` 可以维护 Dubbo filter、上下文 holder、请求头常量、Seata XID 透传和流量治理上下文。
- `utils` 不知道 `UserRpcService`、`MessageRpcService`、`AgentRpcService` 等具体业务契约。
- 如果某个能力只服务单个业务模块，先留在业务服务；只有通用横切能力才进入 `utils`。

## 依赖和 CI

- `utils` 不添加 `implementation "com:rpc-api:..."`。
- 修改 Dubbo 上下文透传能力后，需要编译依赖 `utils` 的 provider/consumer 服务。
- 修改业务 RPC 契约时，去 `../rpc-api` 改，不在 `utils` 新增 `com.kellen.rpc.*`。

## 上下文边界

- 登录用户、租户、数据源、Seata XID、请求 ID、版本号、流量泳道等横切上下文可以由 `utils` 统一透传。
- 业务方法参数、业务 DTO 字段和领域语义必须归属 `rpc-api` 或对应业务服务。

## 调用端统一重试（2.1.1 起）

- utils 对全部同步 Dubbo 调用做两层重试，经 Dubbo SPI 自动生效，不用逐个改引用：
  - 第一层：请求没发出（连接待重连、提供方暂被移出可用列表等复查）的失败，任何方法都按指数退避加抖动重试到这次调用的超时，不计次数。
  - 第二层：请求发出后的连接类失败（Triple UNAVAILABLE、CANCELLED 或 Dubbo 远程异常），只对声明了幂等的方法重试，最多 3 次尝试，并受按（调用方应用，提供方服务）计的令牌桶约束（gRFC A6：10 个令牌，失败扣 1，成功加 0.1，不高于 5 时停）。
  - 超时、业务异常、限流、序列化、鉴权、方法不存在、校验等一律不重试。
- 提供方声明幂等：`@DubboService(methods = @Method(name = "<方法名>", parameters = {"idempotent", "true"}))`，声明经注册中心元数据传到调用端。
  - 只有不改变业务状态、或改变本身幂等的方法才能声明；服务端为执行本身写的日志、审计、评估记录不算业务状态（RFC 9110 §9.2.1）。
  - 写接口不声明；拿不准就不声明，并在交付说明里写明。没声明的方法只有第一层保护。
- 调用端可在 `@DubboReference(methods = @Method(name = "<方法名>", parameters = {"idempotent", "false"}))` 用同名参数覆盖提供方的声明；直连 URL 的引用拿不到提供方元数据，只看调用端参数。
- 不要在调用点再包一层重试，也不要指望引用上的 `retries` 生效：统一重试在每次尝试里都把它设为 0，全服务群只有这一层重试。
- 引用要显式配超时：截止时间按它算；没配时按 Dubbo 默认的 1000 毫秒（不是 Triple 调用端自己的 3000）。上游请求带来的超时倒计时更短时以它为准。
- 异步调用（返回 `CompletableFuture`，或引用 `async = true`）不经统一重试，照 Dubbo 原有行为。
- 可观测：重试、重试后成功与放弃各有 Micrometer 计数器（`dubbo.consumer.retries`、`dubbo.consumer.retry.recoveries`、`dubbo.consumer.retry.giveups`）；重试后成功记 INFO，放弃记 WARN；调用端第一次看到某个方法的幂等声明会记一行 INFO。
