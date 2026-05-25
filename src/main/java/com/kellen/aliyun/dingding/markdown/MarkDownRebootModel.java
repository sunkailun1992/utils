package com.kellen.aliyun.dingding.markdown;

import com.kellen.aliyun.dingding.AtMobiles;
import lombok.Data;

/**
 * @ClassName MarkDownRebootModel
 * @Description 消息
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/6/25 9:13 上午
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
