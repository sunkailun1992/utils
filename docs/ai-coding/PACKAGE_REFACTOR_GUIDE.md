# utils 包结构分类说明

## 当前结论

`utils` 根包工具类已经按能力域拆分到子包，后续不再把新工具类直接放到 `com.kellen.utils` 根包。

## 当前包结构

```text
com.kellen.utils.response      统一响应
com.kellen.utils.exception     异常处理
com.kellen.utils.enumeration   通用枚举与错误码
com.kellen.utils.auth          JWT、RSA、认证辅助
com.kellen.utils.context       租户、数据源等线程上下文
com.kellen.utils.datasource    数据源工具
com.kellen.utils.json          JSON 工具
com.kellen.utils.http          HTTP、IP、Request、地址解析工具
com.kellen.utils.redis         Redis 工具
com.kellen.utils.websocket     WebSocket 工具
com.kellen.utils.convert       Bean、Map、Object、String、Stream、通用转换
com.kellen.utils.reflect       反射与动态调用
com.kellen.utils.math          BigDecimal、公式、数值计算
com.kellen.utils.file          文件、PDF 工具
com.kellen.utils.excel         Excel 工具
com.kellen.utils.validation    通用校验工具
com.kellen.utils.verify        Bean Validation 注解与校验器
com.kellen.utils.redisson      Redisson、分布式锁
com.kellen.utils.annotations   AOP 与通用能力注解
com.kellen.utils.methods       Methods 注解配套模型与判断逻辑
com.kellen.utils.constants     通用常量
com.kellen.utils.email         邮件工具
com.kellen.config.actuator     Actuator 端点与映射配置
com.kellen.config.async        异步线程配置
com.kellen.config.datasource   动态数据源配置
com.kellen.config.elasticsearch Elasticsearch 配置
com.kellen.config.feign        OpenFeign 配置
com.kellen.config.file         文件上传配置
com.kellen.config.mybatis      MyBatis-Plus 配置
com.kellen.config.redis        Redis 缓存配置
com.kellen.config.sensitive    敏感字段配置
com.kellen.config.swagger      OpenAPI / Knife4j 配置
com.kellen.config.web          Web MVC 拦截器配置
com.kellen.config.wechat       微信配置属性
com.kellen.entity              公共实体基类
com.kellen.idempotent          防重复提交组件
com.kellen.security            安全过滤器、认证用户与用户上下文
com.kellen.security.config     安全认证与租户配置属性
com.kellen.aop                 通用请求切面
com.kellen.log                 请求日志、RPC 日志、ES 日志采集存储
com.kellen.aliyun              阿里云、钉钉、短信、OSS、直播、工作流封装
```

## 新增类放置规则

- 统一响应对象放到 `response`。
- 业务异常、全局异常处理放到 `exception`。
- 通用错误码、通用枚举放到 `enumeration`。
- JWT、签名、加解密等认证安全辅助放到 `auth`。
- ThreadLocal 上下文放到 `context`。
- 数据源选择、数据源辅助工具放到 `datasource`。
- JSON 序列化工具放到 `json`。
- HTTP 请求、IP、请求参数、地址解析放到 `http`。
- Redis 读写工具放到 `redis`。
- WebSocket 推送工具放到 `websocket`。
- Bean、Map、Object、String、Stream、DTO 转换放到 `convert`。
- 反射、动态调用放到 `reflect`。
- 金额、费率、公式、数值计算放到 `math`。
- 文件、PDF 放到 `file`，Excel 放到 `excel`。
- 通用校验算法放到 `validation`，Bean Validation 注解放到 `verify`。
- 分布式锁放到 `redisson`。
- Spring Boot 自动配置按职责放到 `com.kellen.config.*` 子包。
- 通用实体基类放到 `com.kellen.entity`。
- 防重复提交运行组件放到 `com.kellen.idempotent`。
- 认证过滤器和用户上下文放到 `com.kellen.security`，安全配置属性放到 `com.kellen.security.config`。
- 第三方平台封装放到平台命名空间，例如 `com.kellen.aliyun.*`。

## 禁止事项

- 不再向 `com.kellen.utils` 根包新增工具类。
- 不新增业务专属公式、业务用户类型、业务字段字典到公共包。
- 不新增含糊包名，例如 `common`、`base`、`misc`。
- 不再新增或恢复 `com.kellen.bean` 包。
- 不用通配导入 `import com.kellen.utils.*`。
- 修改包名后必须同步更新消费者项目 import。

## 验证要求

修改包结构后必须执行：

```bash
mvn -q -DskipTests install
```

如果消费者项目依赖被移动的类，还必须同步执行消费者项目编译，例如：

```bash
cd /Users/sunkailun/Desktop/个人/GitHub/user
./gradlew clean compileJava -x test
```
