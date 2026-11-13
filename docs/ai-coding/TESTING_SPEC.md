# 测试分层规范

## 定位

`utils` 是公共基础能力包，测试重点是工具类、上下文、过滤器、自动配置和消费者兼容性。

## 主流分层

- 单元测试：工具类、DTO、上下文 holder、小逻辑，使用 JUnit 5 + AssertJ。
- 组件测试：Dubbo Filter、AOP、配置类、拦截器等可用真实对象和轻量 Spring 上下文验证。
- 自动配置测试：优先使用 `ApplicationContextRunner` 或最小 Spring 上下文。
- 消费者编译测试：修改公共 API 后必须编译直接依赖的业务服务。

## assertThat 规则

`assertThat` 是断言工具，可以继续使用。它适合验证工具类和小逻辑，但不能把纯对象断言包装成业务接口测试。

## SpringBootTest 规则

`utils` 默认不需要完整 `@SpringBootTest`。只有自动配置、过滤器注册、AOP 或 Spring Bean 生命周期必须验证时，才使用最小 Spring 上下文。

## 外部环境

- 不默认连接真实 Nacos、数据库、Redis、RabbitMQ、OSS 或 MQ。
- 涉及外部组件时优先 fake、stub、Testcontainers 或专用测试 profile。
- 不把测试依赖的真实账号、密钥或生产配置写入仓库。

## 测试稳定性

- 测试结果只能由输入决定，不依赖真实时间、线程调度、连接复用、端口、执行顺序或本机遗留状态。需要时间的逻辑注入 `Clock`；等待异步结果用有总时限的条件等待，不用固定 `Thread.sleep` 赌时序。
- 本地 HTTP 桩服务每个测试独占随机端口，并在 `@AfterEach` 停止。同一测试对同一被测实例请求多次时，桩响应设 `Connection: close`，不依赖 JDK `HttpServer` 的 keep-alive 连接回收时序。更复杂的桩可评估 MockWebServer 或 WireMock，新增依赖按依赖变更处理。
- 连接池、事件循环、静态单例、系统属性等进程级共享状态，不能成为测试之间的隐含依赖；用到时写明隔离方式，不依赖测试类或测试方法的执行顺序。
- 新增或修改涉及网络、并发、超时、流式的测试，合并前本机连续三轮运行该测试类全部通过：`./gradlew test --rerun --tests '<测试类全名>'`。
- 偶发失败按缺陷处理：先查明原因再修，不用自动重试、放宽断言、`@Disabled` 或删除测试换取通过。确需临时禁用时，在注解里写明原因与恢复条件，并在交付说明中列出。
- 本机 macOS 通过不代表 CI 的 Linux 通过；只在一个平台验证过的结论要写明平台。

## 必跑命令

```bash
./gradlew clean test publishToMavenLocal
```
