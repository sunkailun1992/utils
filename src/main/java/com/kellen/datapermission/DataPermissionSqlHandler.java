package com.kellen.datapermission;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus 数据权限 SQL 处理器。
 *
 * @author sunkailun
 * @DateTime 2026/05/27
 * @email 376253703@qq.com
 */
public class DataPermissionSqlHandler implements MultiDataPermissionHandler {

    /**
     * 数据权限配置属性。
     */
    private final DataPermissionProperties properties;

    /**
     * 构造数据权限 SQL 处理器。
     *
     * @param properties 数据权限配置属性
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    public DataPermissionSqlHandler(DataPermissionProperties properties) {
        this.properties = properties; // 保存配置属性，后续按 Nacos 配置决定是否追加 SQL 条件。
    }

    /**
     * 获取数据权限 SQL 片段。
     *
     * @param table             当前 SQL 表
     * @param where             当前 SQL 已有 where 条件
     * @param mappedStatementId 当前 Mapper 方法ID
     * @return 数据权限 SQL 表达式
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        try {
            String sqlSegment = buildSqlSegment(table, mappedStatementId); // 根据当前用户和表规则构建权限条件字符串。
            if (StringUtils.isBlank(sqlSegment)) {
                return null; // 没有可用条件时不修改当前 SQL。
            }
            return CCJSqlParserUtil.parseCondExpression(sqlSegment); // 使用 JSQLParser 转换为 MyBatis-Plus 可拼接的表达式。
        } catch (Exception ignored) {
            return null; // 数据权限解析失败时不抛底层异常，避免误伤业务主流程。
        }
    }

    /**
     * 构建数据权限 SQL 片段。
     *
     * @param table             当前 SQL 表
     * @param mappedStatementId 当前 Mapper 方法ID
     * @return SQL 条件片段
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private String buildSqlSegment(Table table, String mappedStatementId) {
        if (!properties.isEnabled()) {
            return null; // 数据权限未开启时不追加任何条件。
        }
        if (DataPermissionContextHolder.isIgnore()) {
            return null; // 当前线程显式忽略时不追加任何条件。
        }
        if (table == null || StringUtils.isBlank(table.getName())) {
            return null; // 无法识别表名时不追加条件。
        }
        if (ignoreMappedStatement(mappedStatementId)) {
            return null; // 当前 Mapper 方法命中忽略配置时不追加条件。
        }
        String tableName = table.getName().toLowerCase(Locale.ROOT); // 表名统一转小写，避免大小写差异导致配置失效。
        if (ignoreTable(tableName)) {
            return null; // 表名命中忽略配置时不追加条件。
        }
        DataPermissionProperties.TableRule rule = properties.getTableRules().get(tableName); // 读取当前表的权限字段配置。
        if (rule == null) {
            return null; // 未声明表规则时默认跳过，避免给无 dept_id 字段的表追加错误条件。
        }
        SecurityUser user = UserContextHolder.get(); // 读取认证过滤器写入的当前用户快照。
        if (user == null) {
            return null; // 未登录请求不追加数据权限条件，由接口鉴权决定是否放行。
        }
        String dataScope = StringUtils.defaultIfBlank(user.getDataScope(), DataScopeEnum.SELF.name()); // 未声明数据范围时按本人数据兜底。
        String alias = table.getAlias() == null ? table.getName() : table.getAlias().getName(); // 有表别名时使用别名，避免多表 SQL 字段歧义。
        if (DataScopeEnum.ALL.name().equalsIgnoreCase(dataScope)) {
            return null; // 全部数据范围不追加权限条件。
        }
        if (DataScopeEnum.SELF.name().equalsIgnoreCase(dataScope)) {
            return buildSelfSql(alias, rule, user); // 本人数据按用户字段过滤。
        }
        if (DataScopeEnum.DEPT.name().equalsIgnoreCase(dataScope)) {
            return buildDeptSql(alias, rule, user.getDeptId()); // 本部门数据按当前用户部门过滤。
        }
        if (DataScopeEnum.DEPT_TREE.name().equalsIgnoreCase(dataScope) || DataScopeEnum.CUSTOM.name().equalsIgnoreCase(dataScope)) {
            return buildDeptInSql(alias, rule, user); // 部门树和自定义部门按部门集合过滤。
        }
        return buildSelfSql(alias, rule, user); // 未知数据范围按本人数据兜底，避免越权放大。
    }

    /**
     * 构建本人数据权限 SQL。
     *
     * @param alias 当前表别名
     * @param rule  当前表规则
     * @param user  当前用户
     * @return SQL 条件片段
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private String buildSelfSql(String alias, DataPermissionProperties.TableRule rule, SecurityUser user) {
        String column = StringUtils.defaultIfBlank(rule.getUserColumn(), properties.getDefaultUserColumn()); // 优先使用表规则字段，未配置时使用默认创建人字段。
        String userValue = StringUtils.defaultIfBlank(user.getUserId(), user.getUsername()); // 优先用用户ID过滤，缺失时使用用户名兜底。
        if (StringUtils.isBlank(column) || StringUtils.isBlank(userValue)) {
            return null; // 字段或用户值缺失时无法拼接本人权限条件。
        }
        return alias + "." + column + " = '" + escape(userValue) + "'"; // 返回本人权限 SQL 条件。
    }

    /**
     * 构建单部门数据权限 SQL。
     *
     * @param alias  当前表别名
     * @param rule   当前表规则
     * @param deptId 当前部门ID
     * @return SQL 条件片段
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private String buildDeptSql(String alias, DataPermissionProperties.TableRule rule, String deptId) {
        String column = StringUtils.defaultIfBlank(rule.getDeptColumn(), properties.getDefaultDeptColumn()); // 优先使用表规则字段，未配置时使用默认部门字段。
        if (StringUtils.isBlank(column) || StringUtils.isBlank(deptId)) {
            return null; // 字段或部门ID缺失时无法拼接部门权限条件。
        }
        return alias + "." + column + " = '" + escape(deptId) + "'"; // 返回本部门权限 SQL 条件。
    }

    /**
     * 构建多部门数据权限 SQL。
     *
     * @param alias 当前表别名
     * @param rule  当前表规则
     * @param user  当前用户
     * @return SQL 条件片段
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private String buildDeptInSql(String alias, DataPermissionProperties.TableRule rule, SecurityUser user) {
        String column = StringUtils.defaultIfBlank(rule.getDeptColumn(), properties.getDefaultDeptColumn()); // 优先使用表规则字段，未配置时使用默认部门字段。
        Set<String> deptIds = user.getDataScopeDeptIds().stream().filter(StringUtils::isNotBlank).collect(Collectors.toSet()); // 过滤空部门并去重。
        if (StringUtils.isNotBlank(user.getDeptId())) {
            deptIds.add(user.getDeptId()); // 部门树场景把当前用户部门也纳入权限集合。
        }
        if (StringUtils.isBlank(column) || deptIds.isEmpty()) {
            return null; // 字段或部门集合缺失时无法拼接部门权限条件。
        }
        String values = deptIds.stream().map(this::escape).map(value -> "'" + value + "'").collect(Collectors.joining(",")); // 转换为 SQL in 值列表。
        return alias + "." + column + " in (" + values + ")"; // 返回多部门权限 SQL 条件。
    }

    /**
     * 判断 Mapper 方法是否忽略数据权限。
     *
     * @param mappedStatementId Mapper 方法ID
     * @return true 表示忽略数据权限
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private boolean ignoreMappedStatement(String mappedStatementId) {
        return properties.getIgnoreMappedStatementIds().stream().filter(StringUtils::isNotBlank).anyMatch(mappedStatementId::equals); // 完整匹配 Mapper 方法ID。
    }

    /**
     * 判断表是否忽略数据权限。
     *
     * @param tableName 当前表名
     * @return true 表示忽略数据权限
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private boolean ignoreTable(String tableName) {
        return properties.getIgnoreTables().stream().filter(StringUtils::isNotBlank).map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(tableName::equals); // 小写表名匹配忽略配置。
    }

    /**
     * 转义 SQL 字符串值。
     *
     * @param value 原始值
     * @return 转义后的值
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    private String escape(String value) {
        return value.replace("'", "''"); // 单引号按 SQL 字符串规则转义，避免破坏条件表达式。
    }
}
