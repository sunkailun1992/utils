package com.kellen.aliyun.dingding;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.kellen.aliyun.dingding.markdown.CustomMarkDown;
import com.kellen.utils.DynamicSourceTtl;
import com.kellen.utils.constants.UniversalConstant;
import com.kellen.utils.enumeration.NumericEnum;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @Author 孙凯伦
 * @Date 2021/6/25 16:07
 * @Classname SendRebootUtil
 * @Description 保险公司对接钉钉机器人通知
 */
@Slf4j
public class SendRebootUtil {

    private static final String[] AT_MOBILES = {"18252354064", "18667943303", "18667943303", "18252354064"};

    /**
     * 发送钉钉通知
     * @param dingDingParams 发送钉钉的参数
     * @return void
     */
    public static void sendDingNotice(DingDingParams dingDingParams) {
        if(Objects.isNull(dingDingParams) && StringUtils.isBlank(dingDingParams.getReqTitle())){
            return;
        }
        CustomMarkDown customMarkDown = CustomMarkDown.getInstance();
        customMarkDown.level3Title(buildTitle(0, dingDingParams.getReqTitle()));
        if(MapUtils.isNotEmpty(dingDingParams.getListParams())){
            customMarkDown.list(false, dingDingParams.getTextColor(), dingDingParams.getListParams());
        }
        if(Objects.nonNull(dingDingParams.getReqObject())) {
            String reqJson = JSON.toJSONString(dingDingParams.reqObject, true);
            if(reqJson.length() > UniversalConstant.TEN_THOUSAND){
                customMarkDown.codeBlockOverText(reqJson.substring(0, 500), dingDingParams.textColor, "报文长度超出上限-具体内容请查询日志！").horizontalLine();
            } else {
                customMarkDown.codeBlockText(reqJson).horizontalLine();
            }
        }
        if(StringUtils.isNotBlank(dingDingParams.getMqTitle())){
            customMarkDown.level3Title(buildTitle(1, dingDingParams.getMqTitle()));
            customMarkDown.codeBlockText(JSON.toJSONString(dingDingParams.getMqObject(), true)).horizontalLine();
        }
        if(StringUtils.isNotBlank(dingDingParams.getRespTitle())){
            customMarkDown.level3Title(buildTitle(2, dingDingParams.getRespTitle()));
            JSONObject jsonObject = JSON.parseObject(dingDingParams.getRespJson());
            customMarkDown.codeBlockText(JSON.toJSONString(jsonObject, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue)).horizontalLine();
        }

        List<String> telList = Lists.newArrayList();
        if(StringUtils.isNotBlank(dingDingParams.getErrorMsg())) {
            String errorMsg = dingDingParams.getErrorMsg();
            if(errorMsg.length() > UniversalConstant.TWO_HUNDRED) {
                errorMsg = errorMsg.substring(0, 200);
            }
            customMarkDown.color3Title("响应结果-错误信息：", errorMsg, dingDingParams.getTextColor()).horizontalLine();
            telList = dingDingParams.getAtMobileList();
        }
        sendDingDingReboot(dingDingParams.getReqTitle(), dingDingParams.getTextColor(), customMarkDown, telList);
    }

    /**
     * 发送钉钉机器人
     * @param title 标题
     * @param noticeRecipientColor 通知人的颜色
     * @param customMarkDown 自定义markDown
     * @param telList 联系电话
     * @return void
     */
    public static void sendDingDingReboot(String title, String noticeRecipientColor, CustomMarkDown customMarkDown, List<String> telList) {
        try {
            buildActive(customMarkDown);
            if (CollectionUtils.isNotEmpty(telList)){
                customMarkDown.horizontalLine().noticeRecipient(3, noticeRecipientColor, telList);
            }
            String markdown = DingDingUtil.markdown(false, title, customMarkDown.toString(), telList);
            DingDingUtil.sendReboot(markdown);
        } catch (Exception e) {
            log.error("【钉钉机器人】 发送消息失败：", e);
        }
    }

    /**
     *  组织当前的运行环境
     * @param customMarkDown 自定义MarkDown
     * @return void
     */
    private static void buildActive(CustomMarkDown customMarkDown) {
        customMarkDown.colorNotes("当前应用: ", SpringUtil.getActiveProfile(), "#FF4500");
        customMarkDown.colorNotes("当前环境: ", DynamicSourceTtl.get(), "#FF4500");
    }

    /**
     *  组织标题
     * @param i 1是MQ通知报文，2是响应报文，默认是请求报文
     * @param title 标题
     * @return String
     */
    private static String buildTitle(int i, String title){
        if(i == NumericEnum.ONE.getValue()) {
            return "【" + title + "】MQ通知报文";
        }
        if(i == NumericEnum.TWO.getValue()) {
            return "【" + title + "】响应报文";
        }
        return "【" + title + "】请求报文";
    }

    /**
     * 钉钉通知的参数
     */
    @Data
    @Accessors(chain = true)
    public static class DingDingParams{
        private Map<String, String> listParams;
        private String reqTitle;
        private Object reqObject;
        private String mqTitle;
        private Object mqObject;
        private String respTitle;
        private String respJson;
        private String errorMsg;
        private List<String> atMobileList;
        private String textColor = "#FF4500";
    }


    /**
     * 模块枚举
     */
    public enum ModuleEnum {
        //USER
        USER,
        //INTERNAL
        INTERNAL,
        //CHANNEL
        CHANNEL,
        //COMMON
        COMMON,

        ;

        public static List<String> getMobiles(List<Integer> defalutMobileIndexList, ModuleEnum moduleEnum) {
            List<String> atMobiles = Lists.newArrayList();
            if(CollectionUtils.isNotEmpty(defalutMobileIndexList)) {
                for (Integer mobileIndex : defalutMobileIndexList) {
                    if(mobileIndex < 0 || mobileIndex >= AT_MOBILES.length){
                        continue;
                    }
                    atMobiles.add(AT_MOBILES[mobileIndex]);
                }
            }
            if(CollectionUtils.isNotEmpty(atMobiles)){
                return atMobiles;
            }
            if(Objects.isNull(moduleEnum)){
                return atMobiles;
            }
            switch (moduleEnum) {
                case CHANNEL:
                    atMobiles.add(AT_MOBILES[1]);
                    break;
                case INTERNAL:
                    atMobiles.add(AT_MOBILES[2]);
                    break;
                case COMMON:
                    atMobiles.add(AT_MOBILES[3]);
                    break;
                default:
                    atMobiles.add(AT_MOBILES[0]);
                    break;
            }

            return atMobiles;
        }
    }
}