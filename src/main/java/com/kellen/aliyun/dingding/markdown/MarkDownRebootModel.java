package com.kellen.aliyun.dingding.markdown;

import com.kellen.aliyun.dingding.AtMobiles;
import lombok.Data;

/**
 * 消息
 * 
 */
@Data
public class MarkDownRebootModel {
    /**
     * 此消息类型为固定markdown
     */
    public String msgtype = "markdown";

    public MarkDownModel markdown;

    public AtMobiles at;

}
