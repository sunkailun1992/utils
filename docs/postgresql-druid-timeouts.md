# PostgreSQL 与 Druid 超时单位

PostgreSQL JDBC URL 里的 `connectTimeout`、`socketTimeout` 单位是**秒**；Druid 字段与 JDBC
`Connection.getNetworkTimeout()` 的单位是**毫秒**。Druid 1.2.28 初始化连接池时会从 URL 再读一次这两项，
并原样写进毫秒字段：URL 里的 `socketTimeout=30` 会把 30 秒的读取预算覆盖成 30 毫秒。直接通过
PostgreSQL 驱动连接成功，不能证明经过连接池的元数据查询、健康检查或业务 SQL 也正常。

## 连接池契约

- 数据源由 dynamic-datasource 原生配置创建（各服务 `application.yml` 的 `spring.datasource.dynamic` 块）。
  连接与读取超时只写在 `spring.datasource.dynamic.druid.connect-timeout: 3000`、`socket-timeout: 30000`（毫秒）。
- Nacos 里 `spring.datasource.dynamic.datasource.master/gray.url` **不带** `connectTimeout`、`socketTimeout`，
  其它参数（如 `tcpKeepAlive=true`）照常保留。旧配置曾在建池前从 URL 去掉这两项，原生配置没有这一步。
- 也不要把 URL 改成 `socketTimeout=30000` 或 `connectTimeout=3000`：驱动按秒解释，实际预算会变成 30000 秒。
- 连接池最大连接数（master 4、gray 2）、借出等待（3000 毫秒）、校验超时与保活规则同在该块，按服务统一。

## 验证

`NativeDynamicDataSourceTest` 不建立网络连接：用服务的数据源块建出 master、gray 两个池，再以不带超时参数的 URL
完成 Druid 初始化，断言连接超时仍为 3000 毫秒、读取超时仍为 30000 毫秒。2026-11-15 的临时探针实测：同一 URL 带
`connectTimeout=3&socketTimeout=30` 时，初始化后两项分别变成 3 与 30。

更早一轮的隔离复现（Druid `1.2.27`、PostgreSQL JDBC `42.7.10`）：同样的 120 毫秒只读查询，直接驱动连接可以完成，
池连接在约 33 毫秒时抛 `SocketTimeoutException`。运行态恢复仍须由发布验收完成，不能以隔离测试代替。
