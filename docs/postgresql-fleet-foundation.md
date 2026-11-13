# 个人业务库 PostgreSQL 底座

本轮目标是个人 Java fleet 的业务库统一迁到 NAS PostgreSQL。本机不安装或运行 PostgreSQL 服务，专用集成测试库也只在 NAS；本机仅作为 JDBC 客户端。MySQL 中仍供其它项目或中间件使用的库、账号和容器不属于本轮删除范围。公共库不长期保留 MySQL/PG 双方言；切换必须与个人 Nacos 数据源配置、业务 PG schema、消费者版本和私有连接信息一起完成。

## 配置合同

- JDBC 驱动由固定 Spring Boot 4.0.9 BOM 管理为 `org.postgresql:postgresql:42.7.14`，不再公开传递 MySQL 驱动。
- 主库使用 `postgresql.url/username/password/driverClassName`；灰度别名使用 `postgresql-gray.url/username/password/driverClassName`。驱动必须为 `org.postgresql.Driver`，URL 必须使用 `jdbc:postgresql:`。旧 `mysql.*` 不作静默回退。
- `master`、`gray` 路由别名及原有 Seata 代理保持；两者都连接 PG，同库部署可在私有 Nacos 配置中引用同一组 PG 参数。业务请求不能选择任意连接地址。
- 原有 `utils.datasource.health.*` 的秒/毫秒单位、`SELECT 1` 校验、借出检查、空闲保活及保活间隔归一化规则保持。
- 连接预算明确为master最大4、gray最大2、每池缺省minIdle/initialSize均1，七服务缺省初始化共14连接。受信字段 `utils.datasource.pool.master.max-active` 与 `gray.max-active` 可调整分配，但两者均至少1且合计不超过6；`utils.datasource.health.min-idle` 必须非负且不大于任一池上限。`utils.datasource.pool.max-wait-millis` 为1至30000毫秒、默认3000，避免无限等待。调度池如后续启用仍须独立限额，不使用业务AT连接池。
- 分页统一使用 MyBatis-Plus PostgreSQL 方言。`tenant_id` 保持字符串类型，包括 `00100` 一类前导零；缺少必需租户时失败关闭，不依赖 MySQL 数字隐式转换。Boolean 逻辑删除使用 `false/true`。

## 公共 schema 与 Seata

首次启动前在每个选定业务 PG 库手动执行 `src/main/resources/db/postgresql/common-infra-schema.sql`，提供 `public.ddl_history` 与 `public.undo_log`。旧 MySQL 脚本是已发布历史，不改写也不在 PG 执行。脚本仅使用幂等建表/索引，不删除现有库或表；已有同名但不同结构必须先读回核对，`IF NOT EXISTS` 不能替代结构验收。

Seata AT 结构依据 [Apache Seata v2.6.0 官方 PostgreSQL client 脚本](https://github.com/apache/incubator-seata/blob/v2.6.0/script/client/at/db/postgresql.sql)：独立序列主键、`BYTEA` 回滚数据与相同事务身份/时间字段。Server 2.7.0 的配置与服务端存储另行验收，不能以 client schema 存在代替真实跨服务回滚测试。[Seata v2.6 SQL 限制](https://seata.apache.org/docs/user/sqlreference/sql-restrictions/)仍适用，不为了补 MySQL `ON UPDATE` 行为而默默添加不受支持的触发器。

## 启动 DDL 与事务历史

`PostgresqlDdlAutoConfiguration` 通过 Boot `AutoConfiguration.imports` 在官方 `DdlAutoConfiguration` 之前注册 `PostgresqlDdlApplicationRunner`，不依赖消费者组件扫描顺序。仍使用 MyBatis-Plus 3.5.16 的真实 `DdlHelper`、`ScriptRunner` 和 `PostgreDdlGenerator`，保留 `public.ddl_history` 的脚本标识及版本格式；显式自定义的 runner 不会被覆盖。

官方执行器的 `autoCommit=true` 可能留下半个文件并吞掉错误；仅改成 `false` 也不够，因为 `ScriptRunner` 会提交脚本，而其后的最后一条 history 尚未提交。公共 PG 执行器使用专用连接的外层事务：先设 `READ_COMMITTED`、获取数据库内固定的事务级 advisory lock，再将当前事务 `search_path` 限定为 `public`。官方 helper 只拿到私有连接适配器，其内部 `commit/rollback/close` 不结束真实事务；全部脚本及最后一条 history 成功后统一提交。任意脚本或 history 失败都回滚，并以固定错误代码阻止应用就绪，不携带原始 SQL、数据库异常消息或 cause。两个 ScriptRunner writer 均关闭，避免直接向控制台打印 SQL。

原子范围是每个 `IDdl.runScript` 的单次数据源回调，不是跨库事务。并发启动等待同一数据库的锁后按 `READ_COMMITTED` 重新读取已提交 history，避免重复执行。连接归还前恢复自动提交、原隔离级别和事务局部 search path；若 rollback 本身失败，不调用可能提交残留事务的 `setAutoCommit(true)`。

事务保证适用于 PostgreSQL 的可事务化 schema 与数据及其 history；序列取号仍遵循 PG 的非回滚语义，失败后出现编号空洞不等于残留业务行。脚本不得借扩展、外部函数或过程执行数据库外副作用，这类变更不属于启动 migration 合同。

`public.ddl_history` 与 `public.undo_log` 必须已经手动初始化；缺少任一表立即失败，不以应用首次启动代替基础设施初始化。业务 `IDdl` 只列 `db/postgresql/` 下的受信 classpath `.sql` 资源，不接受文件系统同名覆盖、URL、路径穿越、自定义 delimiter 或非 public 的 history generator。当前脚本语法合同只支持可事务化的建表、collation、索引、序列、表变更、注释及受信数据写入；拒绝显式事务、会话命令、过程块、并发索引和其它非事务操作。该预检服务于当前 migration 合同，不是任意 SQL 的安全沙箱。缺少末尾分号也在执行前拒绝，避免官方解析器的内部提交时序影响原子边界。新增其它类型的 migration 需要单独设计和回归。

数据源初始化前的 PostgreSQL/Druid 秒与毫秒归一化见 [超时单位说明](postgresql-druid-timeouts.md)。不能用延长 DDL 事务或跳过失败的方式掩盖连接层的单位错误。

## 验证与切换

本地组件测试实际生成 PG `LIMIT/OFFSET`、租户 SQL 和 Boolean 逻辑删除条件，验证新命名空间绑定、旧命名空间拒绝与 JDBC 驱动可用；不连接业务数据库。

`PostgresqlLiveContractTest` 仅在显式设置 `FLEET_PG_TEST_URL/USERNAME/PASSWORD` 时连接，并拒绝名称不以 `fleet_pg_test_` 开始的数据库。它在独立合成库执行真实 MyBatis-Plus DDL 两遍、校验单条历史、BYTEA/序列往返，并执行租户、分页和逻辑删除 SQL。默认跳过，真实候选 PG 可用后必须另行取证；不把默认 skip 报为真实 PG 已通过。

`PostgresqlDdlAtomicityTest` 从真实自动配置中获取生产 runner，在同一专用 NAS 测试库验证脚本及最后 history 持久化、第二次启动不重放、Spring 启动失败关闭、跨文件及 history 失败回滚、真实锁等待和 public 作用域/连接状态恢复。合成对象有精确名称和脚本标识，测试只清理自己的对象，不清空公共 history，也不读取业务数据。显式 `FLEET_DDL_OFFICIAL_BASELINE=1` 仅用于重现原官方缺陷，其两项原始期望应失败；常规 CI 与验收使用生产实现。

迁移采用新 PG 库与独立角色，保留源 MySQL 数据。先验证 schema/约束/字符大小写/ID与序列、迁移演练和按表对账，再暂停个人业务写入、执行最终同步、切换配置和精确制品，最后验收认证/租户/分页/记忆/问卷/Outbox及监控。失败回滚需匹配旧制品、旧私有配置与源数据库；切换后的新增写入必须单独决定回流策略，不能直接丢弃。
