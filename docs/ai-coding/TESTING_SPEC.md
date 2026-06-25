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

## 必跑命令

```bash
./gradlew clean test publishToMavenLocal
```
