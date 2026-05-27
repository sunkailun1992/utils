package com.kellen.datapermission;

import lombok.Getter;

/**
 * 数据权限范围枚举。
 *
 * @author sunkailun
 * @DateTime 2026/05/27
 * @email 376253703@qq.com
 */
@Getter
public enum DataScopeEnum {

    /**
     * 全部数据。
     */
    ALL("全部数据"),

    /**
     * 仅本人数据。
     */
    SELF("仅本人数据"),

    /**
     * 本部门数据。
     */
    DEPT("本部门数据"),

    /**
     * 本部门及下级部门数据。
     */
    DEPT_TREE("本部门及下级部门数据"),

    /**
     * 自定义部门数据。
     */
    CUSTOM("自定义部门数据");

    /**
     * 数据范围说明。
     */
    private final String desc;

    /**
     * 构造数据范围枚举。
     *
     * @param desc 数据范围说明
     * @author sunkailun
     * @DateTime 2026/05/27
     * @email 376253703@qq.com
     */
    DataScopeEnum(String desc) {
        this.desc = desc; // 保存展示说明，供接口和文档统一使用。
    }
}
