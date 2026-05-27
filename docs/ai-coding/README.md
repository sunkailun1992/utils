# utils AI 编码入口

本目录用于给 AI 或新接手开发者快速理解 `utils` 的当前架构边界、编码规范和工具类归类。修改代码前先读本文件，再按需阅读规范与目录。

## 阅读顺序

1. 先读 [项目编码规范](PROJECT_CODING_SPEC.md)，确认响应、异常、认证、租户、动态数据源、乐观锁和注释规则。
2. 再读 [工具类归类目录](UTILS_TOOL_CATALOG.md)，确认目标能力应该放在哪个包、复用哪个类、哪些历史工具需要谨慎使用。
3. 最后阅读目标 Java 类及其上下游调用点，避免只根据类名猜测行为。

## 修改前检查

- 使用 `rg` 搜索现有实现，优先复用已有工具类。
- 检查是否会影响消费者项目，尤其是 `/Users/sunkailun/Desktop/个人/GitHub/user`。
- 确认没有重新引入旧 `Json` 响应、旧 token 认证、旧多数据源名称或 `javax.*`。
- 如果修改公共 API、注解、AOP、认证、租户、异常、返回值、MyBatis-Plus 配置，修改后必须执行 `mvn -q -DskipTests install`。

## 文档维护

- 新增公共工具类时，同步更新 [工具类归类目录](UTILS_TOOL_CATALOG.md)。
- 新增或调整架构规则时，同步更新 [项目编码规范](PROJECT_CODING_SPEC.md)。
- 根目录 `README.md` 只放项目定位、构建命令和最高优先级约束。

