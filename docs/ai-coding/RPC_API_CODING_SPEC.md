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
