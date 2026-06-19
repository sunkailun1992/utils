package com.kellen.aliyun.dingding.markdown;

import lombok.Data;

/**
 * 消息内容
 * 
 */
@Data
public class MarkDownModel {
    /**
     * 首屏会话透出的展示内容
     */
    private String title;

    /**
     * markdown格式的消息
     */
    private String text;
}
