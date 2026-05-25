package com.kellen.aliyun.dingding;

import lombok.Data;

import java.util.List;

/**
 * @ClassName AtMobiles
 * @Description
 * @Author 孙凯伦
 * 
 * @Email 376253703@qq.com
 * @Time 2021/6/25 9:13 上午
 */
@Data
public class AtMobiles {
    /**
     * 被@人的手机号
     * @return
     */
    private List<String> atMobiles;

    /**
     * @所有人时:true,否则为:false
     */
    private Boolean isAtAll;
}
