# 个人业务库 PostgreSQL 底座

本轮目标是个人 Java fleet 的业务库统一迁到 NAS PostgreSQL。本机不安装或运行 PostgreSQL 服务，专用集成测试库也只在 NAS；本机仅作为 JDBC 客户端。MySQL 中仍供其它项目或中间件使用的库、账号和容器不属于本轮删除范围。公共库不长期保留 MySQL/PG 双方言；切换必须与个人 Nacos 数据源配置、业务 PG schema、消费者版本和私有连接信息一起完成。

## 配置合同

- JDBC 驱动由固定 Spring Boot 4.0.4 BOM 管理为 `org.postgresql:postgresql:42.7.10`，不再公开传递 MySQL 驱动。
- 主库使用 `postgresql.url/username/password/driverClassName`；灰度别名使用 `postgresql-gray.url/username/password/driverClassName`。驱动必须为 `org.postgresql.Driver`，URL 必须使用 `jdbc:postgresql:`。旧 `mysql.*` 不作静默回退。
- `master`、`gray` 路由别名及原有 Seata 代理保持；两者都连接 PG，同库部署可在私有 Nacos 配置中引用同一组 PG 参数。业务请求不能选择任意连接地址。
- 原有 `utils.datasource.health.*` 的秒/毫秒单位、`SELECT 1` 校验、借出检查、空闲保活及保活间隔归一化规则保持。
- 连接预算明确为master最大4、gray最大2、每池缺省minIdle/initialSize均1，七服务缺省初始化共14连接。受信字段 `utils.datasource.pool.master.max-active` 与 `gray.max-active` 可调整分配，但两者均至少1且合计不超过6；`utils.datasource.health.min-idle` 必须非负且不大于任一池上限。`utils.datasource.pool.max-wait-millis` 为1至30000毫秒、默认3000，避免无限等待。调度池如后续启用仍须独立限额，不使用业务AT连接池。
- 分页统一使用 MyBatis-Plus PostgreSQL 方言。`tenant_id` 保持字符串类型，包括 `00100` 一类前导零；缺少必需租户时失败关闭，不依赖 MySQL 数字隐式转换。Boolean 逻辑删除使用 `false/true`。

## 公共 schema 与 Seata

首次启动前在每个选定业务 PG 库手动执行 `src/main/resources/db/postgresql/common-infra-schema.sql`，提供 `public.ddl_history` 与 `public.undo_log`。旧 MySQL 脚本是已发布历史，不改写也不在 PG 执行。脚本仅使用幂等建表/索引，不删除现有库或表；已有同名但不同结构必须先读回核对，`IF NOT EXISTS` 不能替代结构验收。

Seata AT 结构依据 [Apache Seata v2.6.0 官方 PostgreSQL client 脚本](https://github.com/apache/incubator-seata/blob/v2.6.0/script/client/at/db/postgresql.sql)：独立序列主键、`BYTEA` 回滚数据与相同事务身份/时间字段。Server 2.7.0 的配置与服务端存储另行验收，不能以 client schema 存在代替真实跨服务回滚测试。[Seata v2.6 SQL 限制](https://seata.apache.org/docs/user/sqlreference/sql-restrictions/)仍适用，不为了补 MySQL `ON UPDATE` 行为而默默添加不受支持的触发器。

MyBatis-Plus 3.5.16 的 `DdlHelper` 按真实 JDBC URL 选择 `PostgreDdlGenerator`，其历史表默认使用 `public`。业务 `IDdl` 仅列新 PG classpath 脚本；既有 MySQL 历史不能混在新执行列表。默认库级 DDL runner 会记录错误并继续，因此部署必须显式校验 DDL 执行结果和历史记录，不能只看应用启动成功。

## 验证与切换

本地组件测试实际生成 PG `LIMIT/OFFSET`、租户 SQL 和 Boolean 逻辑删除条件，验证新命名空间绑定、旧命名空间拒绝与 JDBC 驱动可用；不连接业务数据库。

`PostgresqlLiveContractTest` 仅在显式设置 `FLEET_PG_TEST_URL/USERNAME/PASSWORD` 时连接，并拒绝名称不以 `fleet_pg_test_` 开始的数据库。它在独立合成库执行真实 MyBatis-Plus DDL 两遍、校验单条历史、BYTEA/序列往返，并执行租户、分页和逻辑删除 SQL。默认跳过，真实候选 PG 可用后必须另行取证；不把默认 skip 报为真实 PG 已通过。

迁移采用新 PG 库与独立角色，保留源 MySQL 数据。先验证 schema/约束/字符大小写/ID与序列、迁移演练和按表对账，再暂停个人业务写入、执行最终同步、切换配置和精确制品，最后验收认证/租户/分页/记忆/问卷/Outbox及监控。失败回滚需匹配旧制品、旧私有配置与源数据库；切换后的新增写入必须单独决定回流策略，不能直接丢弃。
