# 个人业务库 PostgreSQL 底座

本轮目标是个人 Java fleet 的业务库统一迁到 NAS PostgreSQL。本机不安装或运行 PostgreSQL 服务，专用集成测试库也只在 NAS；本机仅作为 JDBC 客户端。MySQL 中仍供其它项目或中间件使用的库、账号和容器不属于本轮删除范围。公共库不长期保留 MySQL/PG 双方言；切换必须与个人 Nacos 数据源配置、业务 PG schema、消费者版本和私有连接信息一起完成。

## 配置合同

- JDBC 驱动由固定 Spring Boot 4.0.9 BOM 管理为 `org.postgresql:postgresql:42.7.14`，不再公开传递 MySQL 驱动。
- 数据源用 dynamic-datasource 原生配置。各服务 `application.yml` 的 `spring.datasource.dynamic` 块固定 `primary: master`、`strict: true`、Druid 连接池参数与 `seata`、`seata-mode: AT`；Nacos 的 `{service}-spring.yaml` 只放 `spring.datasource.dynamic.datasource.master/gray` 的 `url`、`username`、`password`、`driver-class-name`。驱动必须为 `org.postgresql.Driver`，URL 必须使用 `jdbc:postgresql:`，且不带 `connectTimeout`、`socketTimeout`（见[超时单位说明](postgresql-druid-timeouts.md)）。
- `master`、`gray` 路由别名保持；两者都连接 PG，同库部署时 gray 引用 master 的同一组参数。业务请求不能选择任意连接地址。
- 连接预算为 master 最大 4、gray 最大 2，每池 `initial-size`、`min-idle` 均为 1，借出等待 3000 毫秒，七服务缺省初始化共 14 连接；借出前 `SELECT 1` 校验、空闲检测 30 秒、保活 60 秒（须大于检测间隔）。调度池如后续启用仍须独立限额，不使用业务 AT 连接池。
- `seata` 跟随 `seata.enabled`（`${seata.enabled:true}`）：Seata 开启时由 dynamic-datasource 给两个池包 AT 代理，`seata.enable-auto-data-source-proxy: false` 防止 Seata 再包一层路由数据源。
- 分页统一使用 MyBatis-Plus PostgreSQL 方言。`tenant_id` 保持字符串类型，包括 `00100` 一类前导零；缺少必需租户时失败关闭，不依赖 MySQL 数字隐式转换。Boolean 逻辑删除列为 `is_deleted`，取值 `false/true`。

## 表结构与 Seata

表结构只由各服务 `src/main/resources/db/migration` 的 Flyway 迁移管理，utils 不放迁移；已提交的迁移文件不修改，改结构只新增 `V<下一个>__<说明>.sql`；新库保持空库，由服务首次启动时的 Flyway 建表，不手工执行任何公共脚本。已有的库由 `baseline-on-migrate: true`、`baseline-version: 1` 接管：版本 1 登记为基线、不执行，随后执行 V2 起的迁移。Seata AT 的 `undo_log` 写在各服务 `V1__baseline.sql`。

Seata AT 结构依据 [Apache Seata v2.6.0 官方 PostgreSQL client 脚本](https://github.com/apache/incubator-seata/blob/v2.6.0/script/client/at/db/postgresql.sql)：独立序列主键、`BYTEA` 回滚数据与相同事务身份/时间字段。Server 2.7.0 的配置与服务端存储另行验收，不能以 client schema 存在代替真实跨服务回滚测试。[Seata v2.6 SQL 限制](https://seata.apache.org/docs/user/sqlreference/sql-restrictions/)仍适用，不为了补 MySQL `ON UPDATE` 行为而默默添加不受支持的触发器。

## 启动迁移

Flyway 用 `spring.flyway.url/user/password`（引用 master 的同一组地址与账号）单独连库，`validate-on-migrate: true`、`clean-disabled: true`。PostgreSQL 的 DDL 可在事务内执行，Flyway 默认每个迁移一个事务：迁移失败整版回滚，服务启动即停，不会留下半个文件。

启动顺序：MyBatis-Plus 的顺序探测器让 `SqlSessionTemplate` 依赖 `flywayInitializer`，Boot 自带的探测器让 `JdbcTemplate` 同样等待，所以 Mapper 与 JdbcTemplate 在迁移完成后才访问业务表。直接注入 `SqlSessionFactory` 或 `DataSource` 在初始化阶段读业务表的 Bean 不受此保护，不要这样写。

Spring Boot 建 Flyway 时会先取主数据源；Seata 开启时 dynamic-datasource 在建池阶段就包 AT 代理并检查 `undo_log`，表不存在即启动失败。所以 `seata` 跟随 `seata.enabled`：github-dev 关闭 Seata，空库首次启动可由 Flyway 建表；启用 Seata 前库里须已完成迁移（例如先在 Seata 关闭时启动一次）。

## 验证与切换

本地组件测试实际生成 PG `LIMIT/OFFSET`、租户 SQL 和 Boolean 逻辑删除条件，并用服务的数据源块建出两个池核对预算与超时；不连接业务数据库。

以下真库测试仅在显式设置 `FLEET_PG_TEST_URL/USERNAME/PASSWORD` 时连接，并拒绝名称不以 `fleet_pg_test_` 开始的数据库，都在随机独立 schema 里执行并在结束后删除：`PostgresqlLiveContractTest` 连跑两次 `db/test/undo_log.sql` 并核对 BYTEA/序列与微秒精度往返，执行租户、分页和逻辑删除 SQL；`FlywayMybatisOrderingLiveTest` 核对 Flyway 先于 MyBatis；`NativeDynamicDataSourceTest` 核对 Seata 开启时两池包着 AT 代理；`FreshDatabaseStartupLiveTest` 核对空库在 Seata 关闭时启动并由 Flyway 建表、Seata 开启时空库启动失败。默认跳过，不把默认 skip 报为真实 PG 已通过。

迁移采用新 PG 库与独立角色，保留源 MySQL 数据。先验证 schema/约束/字符大小写/ID与序列、迁移演练和按表对账，再暂停个人业务写入、执行最终同步、切换配置和精确制品，最后验收认证/租户/分页/记忆/问卷/Outbox及监控。失败回滚需匹配旧制品、旧私有配置与源数据库；切换后的新增写入必须单独决定回流策略，不能直接丢弃。
