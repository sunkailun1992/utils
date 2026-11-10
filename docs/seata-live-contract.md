# Seata 真实跨进程 PostgreSQL 合同

## 目标与边界

`SeataDubboPostgresqlLiveContractTest` 验证公共基础设施链：已有 TC、两个独立 Java JVM、真实 Dubbo Triple、现有 `DubboContextPropagationFilter`、公共 `DynamicDataSourceConfig` 的 AT 代理，以及两个专用 PostgreSQL 库。

普通 `./gradlew test` 不运行该联调。所有探针类都在 `src/test/java`，不进入 Maven 主制品；不新增业务 RPC、HTTP 写接口或业务全局事务，不改 Nacos、TC 配置或业务库。它使用程序化 Seata TM 开始/提交/回滚全局事务，分支由 Spring `TransactionTemplate` 通过实际 `DataSourceProxy` 提交并注册到 TC；并非用普通 SQL 本地事务代替 Seata。

本合同成功只证明上述基础设施链可执行。实际 fleet 是否启用 `seata.enabled`、是否已配置正确事务分组/注册中心、业务边界是否使用全局事务，需要分别验收。没有跨库业务需求时，不把所有 `@Transactional` 改为 `@GlobalTransactional`。

## 前提与私有环境变量

- 已有 TC 可达，实际服务端版本独立核实。测试不会安装、启动、重启或修改 TC。
- 两个不同的专用 PostgreSQL 数据库，实际库名均以 `fleet_pg_test_` 开头；拒绝所有 `github_dev_*` 业务库。两库各自的账号只须测试库连接、schema 建删表及合成记录读写权限。
- 在两专用库首次启动 RM 前执行权威 `src/main/resources/db/postgresql/common-infra-schema.sql`，准备 `public.undo_log`；测试不会创建、删除或清空该表。
- 只通过环境变量注入以下值，勿写入 Git、命令行参数或日志：

| 变量 | 用途 |
| --- | --- |
| `FLEET_SEATA_LIVE=true` | 明确启用真实联调；缺省跳过 |
| `FLEET_SEATA_TC_ADDRESS` | 已有单 TC 的 `host:port` |
| `FLEET_SEATA_PG_A_URL` / `FLEET_SEATA_PG_B_URL` | 两个不同专用库的 JDBC URL |
| `FLEET_SEATA_PG_A_USERNAME` / `FLEET_SEATA_PG_B_USERNAME` | 各库低权限账号 |
| `FLEET_SEATA_PG_A_PASSWORD` / `FLEET_SEATA_PG_B_PASSWORD` | 对应凭据 |

变量已从私有环境载入后执行：

```bash
./gradlew :test --tests com.kellen.config.seata.SeataDubboPostgresqlLiveContractTest -x :fleet-observability:test --rerun-tasks
```

探针使用独立 file registry/config 直接连接已有 TC，事务分组为测试专用 `fleet_probe_tx`，客户端映射到直连 `default`。它不向 Nacos 发布任何配置/注册信息，不验证生产注册发现路径。两个 JVM 只在 IPv6 loopback `::1` 上暴露临时 Triple 端口，关闭 QoS，不新增公网端口。Dubbo 3.3.6 会拒绝显式 `127.*` bind，因此本机须支持 IPv6 loopback；不自动回落到局域网监听。

定向命令排除无匹配类的观测子模块测试任务；日常完整回归仍执行 `./gradlew test`，包含该子模块。

## 验收标准与实现构成

父 JUnit 创建两库同名随机 `seata_probe_<UUID>` 隔离表和两组合成记录。调用方 JVM A 同时承担 TM 与 RM，提供方 JVM B 只承担 RM。两个 PID 必须不同，且不能是 JUnit 父进程。禁止 in-JVM Dubbo 引用；禁用第三方同类 Seata Dubbo filter，使受验收的现有 utils filter 独立传播 XID，并额外验证合成租户上下文。

1. 正常提交：A 在本地事务中把合成金额从 `100` 改为 `110`；B 通过真实 RPC 把另一库从 `100` 改为 `120`。在全局完成前，独立原生连接必须已经看到双方 after-image 和各自对应 XID 的 `undo_log`。全局提交后两库保持 `110/120`，两边该 XID 的 undo 最终清理。
2. 第二分支失败：A、B 都先完成本地第一阶段提交，B 从独立连接看到 `120` 和 undo 后，再在 RPC 层主动抛错。A 收到真实远程失败并请求 TC 全局回滚；两库均恢复 `100/100`，该 XID 两边 undo 最终清理。这样同时验证两个已提交分支的补偿，避免 B 自己的本地回滚掩盖未参与全局事务。
3. 两端 XID 一致；每轮完成后调用方 ThreadLocal 清理，并通过 TM 协议向 TC 查询该 XID 达到 `Finished`，排除悬挂全局事务。所有写入只针对生成表和合成租户，不查询业务记录，不删除其它 XID 的 undo。

测试直接复用公共动态数据源 Bean，使用最小 Spring 上下文；不全量扫描和连接 Redis、RabbitMQ、Elasticsearch、模型或业务服务。测试自身不启用 Seata 自动数据源代理，沿用公共配置手动代理方式，不改变业务服务的代理开关。

TC 2.7 在 DB 存储模式下会延迟删除已完成会话：[SessionHelper.endRollbacked](https://github.com/apache/incubator-seata/blob/v2.7.0/server/src/main/java/org/apache/seata/server/session/SessionHelper.java) 保留延迟处理路径，[DefaultValues](https://github.com/apache/incubator-seata/blob/v2.7.0/common/src/main/java/org/apache/seata/common/DefaultValues.java) 的 retry dead threshold 默认 70 秒。探针对最终 `Finished` 使用 120 秒的有界轮询，整体测试上限 360 秒；不修改 TC 参数，不把分支已回滚与全局会话已清理混为一个断言。

探针以官方 `dubbo.protocol.triple.rest.enabled=false` 关闭 Triple 的附加 REST 映射，仅验证 Triple RPC。Dubbo 3.3.6 的 Spring REST 适配器会调用 Spring 7 已移除的 `PropertyPlaceholderHelper` 四参数构造器；本合同不宣称该路径兼容，也不修改业务环境开关。该问题必须在 fleet 的真实 provider 导出与 REST 需求范围内另行判断。

## 客户端与 TC 协议核验

判断兼容性不能只看 HTTP 健康端口。官方 [v2.7.0 NettyServerBootstrap](https://github.com/apache/incubator-seata/blob/v2.7.0/core/src/main/java/org/apache/seata/core/rpc/netty/NettyServerBootstrap.java) 在同一个监听链中安装 HTTP/2、Seata 二进制和 HTTP 检测器；[SeataDetector](https://github.com/apache/incubator-seata/blob/v2.7.0/core/src/main/java/org/apache/seata/core/protocol/detector/SeataDetector.java) 按 `0xDA DA` 识别原生帧。[MultiProtocolDecoder](https://github.com/apache/incubator-seata/blob/v2.7.0/core/src/main/java/org/apache/seata/core/rpc/netty/MultiProtocolDecoder.java) 保留 v0/v1/v2 编解码，而 [v2.6.0 ProtocolConstants](https://github.com/apache/incubator-seata/blob/v2.6.0/core/src/main/java/org/apache/seata/core/protocol/ProtocolConstants.java) 使用 v1。

这些源码说明 2.7 TC 具备接收 2.6 原生协议的路径；最终兼容性仍以当前目标端点的真实 RM/TM 注册和两库提交/回滚为准。端口号必须按现场监听、配置和注册结果确定，不因历史默认值或 Compose 暴露条目推定可用，也不因 HTTP 可访问就推断端口不支持原生事务。

TC 使用 MySQL 存储时，对照[官方 v2.7.0 完整 MySQL DDL](https://github.com/apache/incubator-seata/blob/v2.7.0/script/server/db/mysql.sql)核对存储库。`lock_table.status` 是 `TINYINT NOT NULL DEFAULT 0`，并配有 `idx_status`、`idx_xid`；旧表缺状态列会使真实分支注册报错。[官方升级指南](https://seata.apache.org/docs/ops/upgrade/)指出，1.5.0 起已要求补锁状态及调整全局状态索引，不能仅升级镜像而保留旧 schema。修复前备份结构、核对当前事务/分支/锁、明确差异，只对获准部分做迁移；不通过清空 TC 表或业务 undo 避开错误。

## 2026-11-10 现场修复与验收

当日实际 TC 2.7.0 在 `7091` 同时提供健康 HTTP 和 Seata 原生协议；旧默认 `8091` 暴露项存在但连接被拒绝。2.6.0 客户端在 `7091` 的 TM/RM 注册由服务端版本回报确认，随后以本合同完成两库提交及强制下游失败后的两库补偿。合同实际执行 88.524 秒，1 项通过、0 失败、0 跳过；两轮最终 TC 状态均为 `Finished`，最终 global/branch/lock 计数均为 0。这是当日目标基础设施快照，后续运行仍应重验。

原始分支注册确实因 TC 存储 `lock_table` 缺 `status` 而失败。读取当前配置与实际数据库元数据、保存结构备份、确认 branch/lock 都为 0，并逐条核实两个残留 Begin 属于本轮后，仅执行以下迁移：

```sql
ALTER TABLE seata.lock_table
    ADD COLUMN status TINYINT NOT NULL DEFAULT 0 COMMENT '0:locked ,1:rollbacking',
    ADD INDEX idx_status(status),
    ADD INDEX idx_xid(xid);
```

默认 `0` 对应现有 locked 语义，且执行时锁表为空。该语句不是重复执行脚本：再次使用前必须重新检查列、索引和活动事务；发现漂移应停止。执行后读回列的默认值、非空约束及两个索引。没有清空 TC/undo、轮换凭据或重启共享服务；残留事务通过官方 TM 查询均已 `Finished`。

与完整 2.7.0 DDL 相比，当日还发现这些差异，均未纳入本次最小锁表修复：global 的复合索引仍为旧列顺序；branch 的两个时间列仍为 `DATETIME` 而非 `DATETIME(6)`；lock 的 `branch_id` 仍可空、`table_name` 长度为 64 而官方为 32；`vgroup_table` 尚缺。没有缩窄字段、修改历史行、重复插入 `distributed_lock` 或顺带迁移其它表。需要这些特性时，应按目标能力单独评估和迁移，不能把本次合同通过描述成完整服务端 schema 已与官方完全一致。

本次合同使用 file registry、测试专用事务组、Triple RPC 且关闭附加 REST 映射。`github-dev` 共享 Seata 配置在核对时仍为关闭且未提供事务组/注册发现映射；现有业务方法也未定义需要全局原子性的跨库入口。因此基础设施已验收与业务全局事务已接入分开报告，不据此自动启用所有服务或机械增加业务全局事务注解。

## 清理、失败与证据

成功后停止两个 JVM、删除两张随机表和私有临时目录；公共 `undo_log` 保留。若失败且 TC 仍有未补偿 undo，保留隔离表和私有临时证据，不能通过删除 undo 或业务表把失败伪装成清理成功；由操作者先核实 TC 和 RM 状态，再处理专用测试库。

第三方日志和含 TC 地址的 XID 只进入权限 `0700` 的系统临时目录 `fleet-seata-live-*`，节点日志与 TC 配置文件权限 `0600`。测试异常不输出凭据、完整配置、连接串或 XID。只报告断言阶段和执行结果；原始文件不进入仓库或 Wiki。

该变更仅测试与说明，不改变 Maven 主制品或消费者 API，不提升公共制品版本。撤销时移除测试和此文档即可；无业务数据 migration、Nacos 回滚或 TC 回滚步骤。
