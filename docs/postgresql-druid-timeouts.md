# PostgreSQL 与 Druid 超时单位

`DynamicDataSourceConfig` 在创建 `master`、`gray` 连接池时统一处理 PostgreSQL URL 的
`connectTimeout`、`socketTimeout`。这两项在 PostgreSQL JDBC URL 中使用**秒**，Druid
字段和 JDBC `Connection.getNetworkTimeout()` 使用**毫秒**。

当前 Druid 会在初始化时从 URL 再次读取这两项，并直接写入毫秒字段。因此，原始 URL
中的 `socketTimeout=30` 可能让驱动原本的 30 秒读取预算被覆盖为 30 毫秒；直接通过
PostgreSQL 驱动连接成功，不能证明经过连接池的元数据查询、健康检查或业务 SQL 也正常。

## 连接池契约

- 在连接池初始化前，把受信 URL 中的秒数转换为毫秒，并调用 Druid 的
  `setConnectTimeout`、`setSocketTimeout`。
- 从传给 Druid 的 URL 中移除已处理的两项，避免 `init()` 再次用秒数覆盖毫秒字段。
  其它参数的原始编码、字节和先后顺序保持不变。
- 省略任一项时，各自使用原定预算：连接 **3 秒 / 3000 毫秒**，读取
  **30 秒 / 30000 毫秒**。连接池最大连接数、借出等待、校验超时和保活规则不变。
- 显式值必须是正整数秒。拒绝 `0` 的无限等待语义、负数、小数、空值、重复键、大小写
  变体及不能安全转为整数毫秒的值；最大值为 `2147483` 秒。百分号编码的参数名和值
  先解码再检查，编码变体不能绕过重复检测。
- 错误只包含固定拒绝原因，不携带 URL、地址、参数值、凭据或原始解析异常。
- Druid 的 PostgreSQL 连接属性适配会把配置的毫秒值转换回驱动需要的秒。不要把公开
  URL 改成 `socketTimeout=30000` 或 `connectTimeout=3000`；那会改变驱动的实际预算。

例如，合成 URL 的 `connectTimeout=3&socketTimeout=30&tcpKeepAlive=true` 会保留
`tcpKeepAlive=true`，在 Druid 字段分别设置 `3000`、`30000`。初始化后物理 PostgreSQL
连接的 `getNetworkTimeout()` 必须为 `30000`，而不是 `30`。

## 验证与发布边界

`PostgresqlDruidTimeoutTest` 不建立网络连接，覆盖初始化前后的单位、默认值、两池一致性、
编码和参数顺序保留、重复参数及非法值拒绝。

`PostgresqlDruidTimeoutLiveTest` 只在显式提供 `FLEET_PG_TEST_URL` 等测试变量时执行。
它拒绝个人业务库，要求数据库名以 `fleet_pg_test_` 开头且角色同名；只在 NAS 独立测试库
借出一个只读连接，核对 Druid 字段、池连接及解包物理连接的实际超时，执行 120 毫秒只读
查询及限定元数据查询，不创建、更新或删除表和记录。

本轮隔离复现使用原 Jar 中的 Druid `1.2.27` 和 PostgreSQL JDBC `42.7.10`：同样的
120 毫秒只读查询，直接驱动连接可以完成，修复前的池连接约 33 毫秒失败并抛出
`SocketTimeoutException`；候选实现的物理超时为 30000 毫秒，查询成功。独立的 Agent
对照证明 `2.33.1` 加隐私扩展 `0.1.1` 不改变修复前的单位错位。

这些证据证明单位修复及隔离连接行为。九个业务 Jar 重建、受控重启、DDL 历史读回、健康
检查和日志/链路联结仍须由发布验收完成；不能以隔离测试通过代替运行态恢复。
