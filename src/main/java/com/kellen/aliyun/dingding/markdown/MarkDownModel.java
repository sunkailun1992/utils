package com.kellen.aliyun.dingding.markdown;

import lombok.Data;

/**
 * @ClassName MarkDownModel
 * @Description 消息内容
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/6/25 9:14 上午
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
