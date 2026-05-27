package com.kellen.datapermission;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据权限配置属性。
 *
 * @author sunkailun
 * @DateTime 2026/05/27
 * @email 376253703@qq.com
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "security.data-permission")
public class DataPermissionProperties {

    /**
     * 数据权限总开关。
     */
    private boolean enabled = false;

    /**
     * 默认本人数据字段。
     */
    private String defaultUserColumn = "create_name";

    /**
     * 默认部门数据字段。
     */
    private String defaultDeptColumn = "dept_id";

    /**
     * 忽略数据权限的 Mapper 方法ID。
     */
    private List<String> ignoreMappedStatementIds = new ArrayList<>();

    /**
     * 忽略数据权限的数据库表。
     */
    private List<String> ignoreTables = new ArrayList<>();

    /**
     * 参与数据权限的表规则。
     */
    private Map<String, TableRule> tableRules = new HashMap<>();

    /**
     * 数据权限表规则。
     *
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    @Getter
    @Setter
    public static class TableRule {

        /**
         * 本人数据字段。
         */
        private String userColumn;

        /**
         * 部门数据字段。
         */
        private String deptColumn;
    }
}
