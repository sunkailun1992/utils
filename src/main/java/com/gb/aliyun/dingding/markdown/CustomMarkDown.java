package com.gb.aliyun.dingding.markdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * @Author: sunx
 * @Date 2021/6/25 16:07
 * @Classname SendRebootUtil
 * @Description 自定义markDown
 */
@Slf4j
@RequiredArgsConstructor(staticName = "getInstance")
public class CustomMarkDown {

    private StringBuilder stringBuilder = new StringBuilder();

    /**
     * 3级标题
     * @param title 标题
     * @return CustomMarkDown
     */
    public CustomMarkDown level3Title(String title){
        return this.colorText(title, null, 3, true);
    }

    /**
     * 3级彩色标题
     * @param title 标题
     * @param text 文本
     * @param textColor 文本颜色
     * @return CustomMarkDown
     */
    public CustomMarkDown color3Title(String title, String text, String textColor){
        this.stringBuilder.append("###").append(StringUtils.SPACE).append(title);
        return this.colorText(text, textColor, 0, true);
    }

    /**
     * 颜色备注
     * @param title 备注标题
     * @param text 备注文本
     * @param textColor 备注文本颜色
     * @return CustomMarkDown
     */
    public CustomMarkDown colorNotes(String title, String text, String textColor){
        this.stringBuilder.append("######").append(StringUtils.SPACE).append(title);
        return this.colorText(text, textColor, 0, true);
    }

    /**
     * 通知到人
     * @param textGrade 文字等级
     * @param textColor 文字颜色
     * @param telList 通知到手机号
     * @return CustomMarkDown
     */
    public CustomMarkDown noticeRecipient(int textGrade, String textColor, List<String> telList){
       while(textGrade > 0 ){
           this.stringBuilder.append("#");
           textGrade -- ;
       }
        this.stringBuilder.append(StringUtils.SPACE);
        this.stringBuilder.append("<font color=\"").append(textColor).append("\">").append("@" + String.join("@", telList)).append("</font>").append("\n\n");
        return this;
    }

    /**
     * 列表
     * @param ordered 是否有序
     * @param textColor 文本颜色
     * @param textMap 文本内容
     * @return CustomMarkDown
     */
    public CustomMarkDown list(boolean ordered, String textColor, Map<String, String> textMap){
        if(MapUtils.isEmpty(textMap)){
            return this;
        }
        for(Map.Entry<String, String> iterator : textMap.entrySet()) {
            if(ordered) {
                this.stringBuilder.append("1.");
            } else {
                this.stringBuilder.append("+");
            }
            this.stringBuilder.append(StringUtils.SPACE);
            this.stringBuilder.append("#####").append(StringUtils.SPACE).append(iterator.getKey() + "：");
            this.colorText(iterator.getValue(), textColor, 0, true);
        }
        return this;
    }

    /**
     * 代码块文本
     * @param text 文本
     * @return CustomMarkDown
     */
    public CustomMarkDown codeBlockText(String text){
        this.stringBuilder.append("```\n")
                .append(text).append("\n```").append("\n");
        return this;
    }

    /**
     * 代码块溢出文本
     * @param text 文本
     * @return CustomMarkDown
     */
    public CustomMarkDown codeBlockOverText(String text,  String textColor, String overTip){
        this.stringBuilder.append("```\n")
                .append(text)
                .append("\n")
                .append("......")
                .append("\n")
                .append("\n```")
                .append("\n");
        this.colorText(overTip, textColor);
        return this;
    }

    /**
     * 颜色文本
     * @param text 文本
     * @param textColor 文本颜色
     * @return CustomMarkDown
     */
    public CustomMarkDown colorText(String text, String textColor){
        this.stringBuilder.append("<font color=\"").append(textColor).append("\">").append(text).append("</font>").append("\n");
        return this;
    }

    /**
     * 水平线
     * @return CustomMarkDown
     */
    public CustomMarkDown horizontalLine(){
        this.stringBuilder.append("***").append(StringUtils.SPACE).append("\n\n");
        return this;
    }

    /**
     * 换行
     * @return CustomMarkDown
     */
    public CustomMarkDown lineFeed(){
        this.stringBuilder.append("\n\n");
        return this;
    }

    /**
     * 具体体文字处理
     * @return CustomMarkDown
     */
    private CustomMarkDown colorText(String text, String textColor, int textGrade, boolean lineFeed){
        //1、超过范围的，默认四级标题
        if(textGrade > 7){
            textGrade = 4;
        }
        for (int i = textGrade; i > 0; i--) {
            this.stringBuilder.append("#");
        }
        this.stringBuilder.append(StringUtils.SPACE);
        //2、设置标题颜色
        if(StringUtils.isNotBlank(textColor)){
            this.stringBuilder.append("<font color=\"").append(textColor).append("\">").append(text).append("</font>");
        } else {
            this.stringBuilder.append(text);
        }
        //3、换行
        if(lineFeed){
            this.stringBuilder.append("\n\n");
        }
        return this;
    }

    @Override
    public String toString() {
        return this.stringBuilder.toString();
    }
}