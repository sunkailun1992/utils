package com.kellen.aliyun.workflow;

/**
 * 阿里云函数工作流区域枚举。
 *
 * @author 孙凯伦
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
