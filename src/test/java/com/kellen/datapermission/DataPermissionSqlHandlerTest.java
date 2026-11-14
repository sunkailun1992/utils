package com.kellen.datapermission;

import com.kellen.security.SecurityUser;
import com.kellen.security.UserContextHolder;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 固定数据权限生成的条件文本，并验证内部错误不会被吞掉变成「不加条件、放开全部数据」。
 */
class DataPermissionSqlHandlerTest {

    private DataPermissionSqlHandler handler;

    @BeforeEach
    void setUp() {
        DataPermissionProperties properties = new DataPermissionProperties();
        properties.setEnabled(true);
        DataPermissionProperties.TableRule rule = new DataPermissionProperties.TableRule();
        rule.setUserColumn("id");
        rule.setDeptColumn("dept_id");
        properties.getTableRules().put("auth_user", rule);
        handler = new DataPermissionSqlHandler(properties);
    }

    @AfterEach
    void clearUser() {
        UserContextHolder.clear();
    }

    @Test
    void selfScopeFiltersByUserColumnWithAlias() {
        UserContextHolder.set(user("u1", "d1", "SELF", List.of()));

        assertThat(condition()).isEqualTo("u.id = 'u1'");
    }

    @Test
    void deptScopeFiltersByDeptColumn() {
        UserContextHolder.set(user("u1", "d1", "DEPT", List.of()));

        assertThat(condition()).isEqualTo("u.dept_id = 'd1'");
    }

    @Test
    void deptTreeScopeBuildsInList() {
        UserContextHolder.set(user("u1", "d1", "DEPT_TREE", List.of("d2")));

        assertThat(condition()).startsWith("u.dept_id IN (").contains("'d1'").contains("'d2'");
    }

    @Test
    void allScopeAddsNoCondition() {
        UserContextHolder.set(user("u1", "d1", "ALL", List.of()));

        assertThat(handler.getSqlSegment(aliasedUserTable(), null, "mapper.select")).isNull();
    }

    @Test
    void quotesInValuesAreEscaped() {
        UserContextHolder.set(user("a'b", "d1", "SELF", List.of()));

        assertThat(condition()).isEqualTo("u.id = 'a''b'");
    }

    @Test
    void unexpectedErrorsAreNotSwallowed() {
        UserContextHolder.set(new SecurityUser("u1", "name", "t1", "d1", "DEPT_TREE", List.of(), List.of()) {
            @Override
            public List<String> getDataScopeDeptIds() {
                throw new IllegalStateException("broken user context");
            }
        });

        assertThrows(IllegalStateException.class,
                () -> handler.getSqlSegment(aliasedUserTable(), null, "mapper.select"));
    }

    private String condition() {
        Expression expression = handler.getSqlSegment(aliasedUserTable(), null, "mapper.select");
        assertThat(expression).isNotNull();
        return expression.toString();
    }

    private static Table aliasedUserTable() {
        Table table = new Table("auth_user");
        table.setAlias(new Alias("u", false));
        return table;
    }

    private static SecurityUser user(String userId, String deptId, String dataScope, List<String> scopeDeptIds) {
        return new SecurityUser(userId, "name", "t1", deptId, dataScope, scopeDeptIds, List.of());
    }
}
