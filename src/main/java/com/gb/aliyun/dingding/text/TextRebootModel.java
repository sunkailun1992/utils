package com.gb.aliyun.dingding.text;

import com.gb.aliyun.dingding.AtMobiles;
import lombok.Data;

/**
 * @ClassName TextRebootModel
 * @Description 消息
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/6/25 9:12 上午
 */
@Data
public class TextRebootModel {
    /**
     * 此消息类型为固定text
     */
    public String msgtype = "text";

    public ContentModel text;

    public AtMobiles at;
}
