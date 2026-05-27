package com.kellen.aliyun.dingding;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.kellen.aliyun.dingding.markdown.CustomMarkDown;
import com.kellen.utils.context.DynamicSourceTtl;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 钉钉机器人 Markdown 通知工具。
 *
 * <p>本工具只负责组装通用请求、消息、响应、错误信息和运行环境，不内置业务模块、固定手机号或业务文案。</p>
 *
 * @author 孙凯伦
 */
@Slf4j
public class SendRebootUtil {

    /**
     * 请求报文标题类型。
     */
    private static final int REQUEST_TITLE_TYPE = 0;

    /**
     * 消息报文标题类型。
     */
    private static final int MESSAGE_TITLE_TYPE = 1;

    /**
     * 响应报文标题类型。
     */
    private static final int RESPONSE_TITLE_TYPE = 2;

    /**
     * 请求报文最大展示长度。
     */
    private static final int REQUEST_BODY_MAX_LENGTH = 10_000;

    /**
     * 请求报文超长时的摘要展示长度。
     */
    private static final int REQUEST_BODY_SUMMARY_LENGTH = 500;

    /**
     * 错误信息最大展示长度。
     */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 200;

    /**
     * 默认强调色。
     */
    private static final String DEFAULT_TEXT_COLOR = "#FF4500";

    /**
     * 发送通用钉钉通知。
     *
     * @param dingDingParams 钉钉通知参数
     */
    public static void sendDingNotice(DingDingParams dingDingParams) {
        if (Objects.isNull(dingDingParams) || StringUtils.isBlank(dingDingParams.getReqTitle())) { // 参数为空或标题为空时无法组成有效通知。
            return; // 无有效通知内容时直接返回，避免发送空消息。
        }
        CustomMarkDown customMarkDown = CustomMarkDown.getInstance(); // 创建 Markdown 构建器。
        customMarkDown.level3Title(buildTitle(REQUEST_TITLE_TYPE, dingDingParams.getReqTitle())); // 先写入请求标题，保证消息有明确主题。
        appendListParams(customMarkDown, dingDingParams); // 写入列表参数，适合展示键值摘要。
        appendRequestBody(customMarkDown, dingDingParams); // 写入请求报文，超长时自动截断。
        appendMessageBody(customMarkDown, dingDingParams); // 写入消息报文，例如 MQ 或异步事件。
        appendResponseBody(customMarkDown, dingDingParams); // 写入响应报文，按 JSON 美化展示。
        List<String> atMobileList = appendErrorMessage(customMarkDown, dingDingParams); // 写入错误信息，并决定是否 @ 指定手机号。
        sendDingDingReboot(dingDingParams.getReqTitle(), customMarkDown, atMobileList); // 统一发送 Markdown 通知。
    }

    /**
     * 发送已经组装好的钉钉 Markdown 通知。
     *
     * @param title          通知标题
     * @param customMarkDown Markdown 构建器
     * @param atMobileList   需要 @ 的手机号列表
     */
    public static void sendDingDingReboot(String title, CustomMarkDown customMarkDown, List<String> atMobileList) {
        try {
            buildActive(customMarkDown); // 追加当前应用和数据源环境，方便定位问题。
            List<String> safeAtMobileList = safeList(atMobileList); // 空手机号列表统一转换为空集合，避免下游空指针。
            if (!safeAtMobileList.isEmpty()) { // 只有明确传入手机号时才追加通知人。
                customMarkDown.horizontalLine().noticeRecipient(3, DEFAULT_TEXT_COLOR, safeAtMobileList); // 在消息末尾追加 @ 人员。
            }
            String markdown = DingDingUtil.markdown(false, title, customMarkDown.toString(), safeAtMobileList); // 组装钉钉机器人 Markdown 请求体。
            DingDingUtil.sendReboot(markdown); // 通过通用钉钉机器人工具发送消息。
        } catch (Exception e) {
            log.error("钉钉机器人通知发送失败，title: {}", title, e); // 通知失败不能影响主业务流程，只记录错误日志。
        }
    }

    /**
     * 写入列表参数。
     *
     * @param customMarkDown Markdown 构建器
     * @param params         钉钉通知参数
     */
    private static void appendListParams(CustomMarkDown customMarkDown, DingDingParams params) {
        if (MapUtils.isNotEmpty(params.getListParams())) { // 列表参数非空时才展示。
            customMarkDown.list(false, params.getTextColor(), params.getListParams()); // 使用 Markdown 列表展示键值摘要。
        }
    }

    /**
     * 写入请求报文。
     *
     * @param customMarkDown Markdown 构建器
     * @param params         钉钉通知参数
     */
    private static void appendRequestBody(CustomMarkDown customMarkDown, DingDingParams params) {
        if (Objects.nonNull(params.getReqObject())) { // 请求对象非空时才展示请求报文。
            String reqJson = JSON.toJSONString(params.getReqObject(), true); // 将请求对象序列化为格式化 JSON。
            if (reqJson.length() > REQUEST_BODY_MAX_LENGTH) { // 请求报文过长时避免钉钉消息超限。
                String summary = reqJson.substring(0, REQUEST_BODY_SUMMARY_LENGTH); // 截取前半段摘要用于快速定位。
                customMarkDown.codeBlockOverText(summary, params.getTextColor(), "报文长度超出上限，完整内容请查询日志。").horizontalLine(); // 展示摘要并提示查看日志。
            } else {
                customMarkDown.codeBlockText(reqJson).horizontalLine(); // 报文长度可控时完整展示。
            }
        }
    }

    /**
     * 写入消息报文。
     *
     * @param customMarkDown Markdown 构建器
     * @param params         钉钉通知参数
     */
    private static void appendMessageBody(CustomMarkDown customMarkDown, DingDingParams params) {
        if (StringUtils.isNotBlank(params.getMessageTitle())) { // 消息标题非空时展示消息报文。
            customMarkDown.level3Title(buildTitle(MESSAGE_TITLE_TYPE, params.getMessageTitle())); // 写入消息报文标题。
            customMarkDown.codeBlockText(JSON.toJSONString(params.getMessageObject(), true)).horizontalLine(); // 格式化展示消息对象。
        }
    }

    /**
     * 写入响应报文。
     *
     * @param customMarkDown Markdown 构建器
     * @param params         钉钉通知参数
     */
    private static void appendResponseBody(CustomMarkDown customMarkDown, DingDingParams params) {
        if (StringUtils.isNotBlank(params.getRespTitle()) && StringUtils.isNotBlank(params.getRespJson())) { // 响应标题和响应 JSON 同时存在时才展示。
            customMarkDown.level3Title(buildTitle(RESPONSE_TITLE_TYPE, params.getRespTitle())); // 写入响应报文标题。
            JSONObject jsonObject = JSON.parseObject(params.getRespJson()); // 将响应字符串解析为 JSON 对象。
            String prettyJson = JSON.toJSONString(jsonObject, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue); // 格式化响应 JSON。
            customMarkDown.codeBlockText(prettyJson).horizontalLine(); // 将响应 JSON 写入 Markdown 代码块。
        }
    }

    /**
     * 写入错误信息。
     *
     * @param customMarkDown Markdown 构建器
     * @param params         钉钉通知参数
     * @return 需要 @ 的手机号列表
     */
    private static List<String> appendErrorMessage(CustomMarkDown customMarkDown, DingDingParams params) {
        if (StringUtils.isBlank(params.getErrorMsg())) { // 无错误信息时不追加错误段落。
            return Collections.emptyList(); // 无错误时默认不 @ 任何人。
        }
        String errorMsg = params.getErrorMsg(); // 读取原始错误信息。
        if (errorMsg.length() > ERROR_MESSAGE_MAX_LENGTH) { // 错误信息过长时截断，避免通知不可读。
            errorMsg = errorMsg.substring(0, ERROR_MESSAGE_MAX_LENGTH); // 只保留前 200 个字符。
        }
        customMarkDown.color3Title("响应结果-错误信息：", errorMsg, params.getTextColor()).horizontalLine(); // 以强调色展示错误摘要。
        return safeList(params.getAtMobileList()); // 有错误时按调用方传入的手机号列表通知。
    }

    /**
     * 追加当前运行环境。
     *
     * @param customMarkDown Markdown 构建器
     */
    private static void buildActive(CustomMarkDown customMarkDown) {
        customMarkDown.colorNotes("当前应用: ", SpringUtil.getActiveProfile(), DEFAULT_TEXT_COLOR); // 展示当前 Spring profile。
        customMarkDown.colorNotes("当前数据源: ", DynamicSourceTtl.get(), DEFAULT_TEXT_COLOR); // 展示当前动态数据源。
    }

    /**
     * 构建消息段落标题。
     *
     * @param type  标题类型
     * @param title 标题文本
     * @return 带语义前缀的标题
     */
    private static String buildTitle(int type, String title) {
        if (type == MESSAGE_TITLE_TYPE) { // 消息报文标题。
            return "【" + title + "】消息报文"; // 返回消息报文标题。
        }
        if (type == RESPONSE_TITLE_TYPE) { // 响应报文标题。
            return "【" + title + "】响应报文"; // 返回响应报文标题。
        }
        return "【" + title + "】请求报文"; // 默认返回请求报文标题。
    }

    /**
     * 获取安全列表。
     *
     * @param list 原始列表
     * @return 非空列表
     */
    private static List<String> safeList(List<String> list) {
        return Objects.isNull(list) ? Collections.emptyList() : list; // 将 null 统一转换为空集合。
    }

    /**
     * 钉钉通知参数。
     */
    @Data
    @Accessors(chain = true)
    public static class DingDingParams {

        /**
         * 列表展示参数。
         */
        private Map<String, String> listParams;

        /**
         * 请求报文标题。
         */
        private String reqTitle;

        /**
         * 请求报文对象。
         */
        private Object reqObject;

        /**
         * 消息报文标题。
         */
        private String messageTitle;

        /**
         * 消息报文对象。
         */
        private Object messageObject;

        /**
         * 响应报文标题。
         */
        private String respTitle;

        /**
         * 响应报文 JSON 字符串。
         */
        private String respJson;

        /**
         * 错误摘要信息。
         */
        private String errorMsg;

        /**
         * 需要 @ 的手机号列表。
         */
        private List<String> atMobileList;

        /**
         * Markdown 强调色。
         */
        private String textColor = DEFAULT_TEXT_COLOR;
    }
}
