package com.gb.aliyun.workflow;

/**
 * TODO 工作流区域id
 *
 * @author 孙凯伦
 * @className WorkflowRegion
 * @email 376253703@qq.com
 * 
 * @time 2022/2/23 10:06 AM
 */

public enum WorkflowRegion {
    /**
     * 杭州地址
     */
    杭州( "cn-hangzhou"),
    ;
    private String type;

    WorkflowRegion(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}
