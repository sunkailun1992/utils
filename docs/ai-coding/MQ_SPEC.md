# 消息队列规范（RocketMQ）

> 涉及消息收发、主题与消费组、RocketMQ 账号或泳道时必读。Nacos 配置注释遵循 [Nacos 注释与归类规范](NACOS_COMMENT_SPEC.md)。

## 定位

- 个人项目的消息队列是 NAS 上的 RocketMQ 5.5（NameServer、单 broker、dashboard，开 ACL 2.0），编排与账号见 [个人环境说明](../../ops/github-dev/README.md)。
- utils 的消息模块基于 rocketmq-spring 2.3.6（`rocketmq-spring-boot-starter`），客户端锁 5.5.1，与服务端同一版本线；做法照芋道的 `TenantRocketMQInitializer`。
- 模块提供三样东西：发送工具 `MqTemplate`；收发钩子（租户、操作人、数据源标记随消息传递）；按实例泳道隔离消费组与订阅。消费直接用 rocketmq-spring 的 `@RocketMQMessageListener`。
- 只在服务配置了 `rocketmq.name-server` 时生效。没配的服务不建任何 RocketMQ Bean，也不连 RocketMQ。

## Nacos 配置

地址是共享键，放 `reuse-configuration.yaml`：`custom.infra-rocketmq-host`、`custom.infra-rocketmq-port`。账号与口令只来自私有 dev.env 渲染（`GITHUB_ROCKETMQ_*`），不写进 Git。不建共享的 `rocketmq.yaml`：每个服务的账号不同，放各自的 `<服务>-spring.yaml`。

发送方（如 `user-spring.yaml`）：

```yaml
rocketmq:
  # NameServer 地址，引用 reuse-configuration.yaml 的共享键
  name-server: ${custom.infra-rocketmq-host}:${custom.infra-rocketmq-port}
  producer:
    # 生产者组：github-dev-<服务>-producer
    group: github-dev-user-producer
    # 本服务的 RocketMQ 账号（ACL 2.0），值由私有 dev.env 渲染
    access-key: ${GITHUB_ROCKETMQ_USER_ACCESS_KEY}
    secret-key: ${GITHUB_ROCKETMQ_USER_SECRET_KEY}
fleet:
  mq:
    topic:
      # 账号已创建事件的主题
      user-account-created: github-dev-user-account-created
```

消费方（如 `message-spring.yaml`）：

```yaml
rocketmq:
  name-server: ${custom.infra-rocketmq-host}:${custom.infra-rocketmq-port}
  consumer:
    # 监听容器缺省读这两个键
    access-key: ${GITHUB_ROCKETMQ_MESSAGE_ACCESS_KEY}
    secret-key: ${GITHUB_ROCKETMQ_MESSAGE_SECRET_KEY}
fleet:
  mq:
    topic:
      user-account-created: github-dev-user-account-created
    group:
      # 消费组：github-dev-<消费方>-<事件>
      user-account-created: github-dev-message-user-account-created
```

## 发送

- 注入 `MqTemplate`，主题从配置读（`@Value("${fleet.mq.topic.user-account-created}")`），不在代码里写死。
- `send(topic, payload)`、`send(topic, payload, options)`：同步发送，失败抛 `MqSendException`；broker 没有返回 `SEND_OK` 也算失败。负载是对象时按 JSON 序列化。
- `sendAfterCommit(topic, payload[, options])`：在事务里调用时立刻抓取上下文快照，事务提交后才发送，回滚不发；没有事务时立即发送。提交后发送失败只记错误日志（目的地、key、租户与异常类名），不抛出。需要可靠投递的业务自己补偿（例如定时比对补发）。
- 选项 `MqSendOptions.none()` 起步：
  - `withTag(tag)`：目的地变成 `主题:tag`；
  - `withKeys(k1, k2...)`：消息 key，便于在 dashboard 按 key 查消息；放事件 ID 等，不放个人信息；
  - `withDelay(Duration)`：延时投递，按秒取整，1 秒到 3 天；
  - `withOrderKey(key)`：同一顺序键的消息进同一个队列，配合顺序消费；不能与延时同时用。
- 每条消息带上这些属性（属性名统一 `fleet_` 前缀，可直接用在 SQL 过滤里）：租户 `fleet_tenant_id`、操作人 `fleet_user_id` 与 `fleet_dept_id`、数据源标记 `fleet_datasource`（有才写），以及本实例的泳道 `fleet_lane` 与灰度标签 `fleet_canary_tag`。灰度授权与 Seata XID 不传。
- 也可以直接注入 `RocketMQTemplate` 做同步发送，发送钩子同样补上这些属性。不要用它的 `asyncSend*`、`sendOneWay*`：客户端在自己的线程里执行发送钩子，读不到调用方的租户与操作人。

## 消费

```java
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "${fleet.mq.topic.user-account-created}",
        consumerGroup = "${fleet.mq.group.user-account-created}", maxReconsumeTimes = 3)
public class UserAccountCreatedConsumer implements RocketMQListener<UserAccountCreatedEvent> {

    private final UserMessageService userMessageService;

    @Override
    public void onMessage(UserAccountCreatedEvent event) {
        userMessageService.sendWelcomeNotice(event); // 按事件 ID 去重，重复投递时什么都不做。
    }
}
```

- 主题与消费组用 `${...}` 引用 Nacos 配置。
- 只支持单条消费，不调大 `consumeMessageBatchMaxSize`（缺省 1）：消费钩子按单条还原上下文，批量时抛异常。
- `onMessage` 执行前，钩子已按消息还原租户（`TenantContextHolder`）、操作人（`SecurityUser`，只有用户 ID、租户与部门，没有角色和权限）和数据源标记；执行后恢复原值。
- 必须幂等：RocketMQ 至少投递一次，重试与重新负载均衡都会重复投递。用事件 ID 做唯一约束（如部分唯一索引），撞上时当作已处理。
- 抛出异常就重试，`maxReconsumeTimes` 次后进死信主题 `%DLQ%<消费组>`；不该重试的错误自己记日志后正常返回。
- 消费逻辑不调需要灰度授权的 RPC（灰度授权不随消息传递）。消费线程上没有 HTTP 请求的数据权限上下文，需要按数据权限查询时按还原的操作人自己处理。
- 顺序消费用 `consumeMode = ConsumeMode.ORDERLY`，发送方用 `withOrderKey`。

## 泳道

- 泳道与灰度标签取本实例的 Nacos 注册元数据 `traffic.lane`（缺省 `stable`）与 `canary.tag`，不看请求头。
- 稳定实例：消费组名不变，只收 `fleet_lane` 为空或为 `stable` 的消息。
- 其他泳道：消费组名自动加 `-<泳道>-<标签>`（没有标签只加泳道），只收本泳道、本标签的消息。
- 订阅统一改成 SQL92，监听器原有的 tag 过滤并进同一条 SQL；broker 须开 `enablePropertyFilter=true`（NAS 已开）。
- 所以灰度实例发出的消息只有同泳道、同标签的消费者收。没有这样的消费者在线时没人处理；之后才上线的新消费组可能从最新位点开始，读不到之前的消息。

## 命名与账号

- 主题 `github-dev-<生产方>-<事件>`；消费组 `github-dev-<消费方>-<事件>`；生产者组 `github-dev-<服务>-producer`；账号 `github-dev-<服务>`。
- broker 关了自动建主题（`autoCreateTopicEnable=false`）：新主题在 NAS 编排的初始化任务里建并授权。消费组由 broker 自动创建，账号按 `Group:github-dev-<服务>-*` 前缀授权，灰度后缀也在前缀内。
- 授权（ACL 2.0）：生产者对主题 `Pub`；消费者对主题 `Sub,Get`（新消费组第一次启动要查队列最大位点，这个请求要 `Get`），对消费组 `Sub`。同一个资源只写在一条授权里：`updateAcl` 按资源整条替换动作，分两条写会互相覆盖。

## 分包

- 消息类放 `mq/message`，发送方放 `mq/producer`，消费方放 `mq/consumer`（芋道的分包）。
- 跨服务的事件类放 `rpc-api`（如 `com.kellen.rpc.user.event.UserAccountCreatedEvent`），只放 ID 与必要的展示字段。

## 禁止事项

- 不直接用 `DefaultMQProducer`、`DefaultMQPushConsumer` 等原生客户端：绕开了上下文、泳道与账号配置。
- 不用 `RocketMQTemplate` 的异步发送与单向发送。
- 不批量消费；不在消费逻辑里调需要灰度授权的 RPC。
- 消息里不放口令、令牌等凭据，也不放完整个人信息（手机号、证件号、住址等）。

## 测试

- 单元测试：发送方模拟 `MqTemplate` 或 `RocketMQTemplate`；消费方直接调监听器的 `onMessage`。
- 真库测试只在本机跑：变量 `FLEET_MQ_TEST_NAMESRV`、`FLEET_MQ_TEST_ACCESS_KEY`、`FLEET_MQ_TEST_SECRET_KEY` 放在本机私有的 env 文件里，CI 里没有这些变量，测试整类跳过。写法见 utils 的 `RocketMqLiveTest`：消费组名带本次运行的时间戳，消息内容带唯一标记（新消费组可能从头读到以前运行留下的消息），发送前等消费者分到全部队列。
