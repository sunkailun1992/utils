package com.kellen.aliyun.dingding.text;

import com.kellen.aliyun.dingding.AtMobiles;
import lombok.Data;

/**
 * 消息
 * 
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
