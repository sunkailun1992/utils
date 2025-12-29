package com.gb.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 字符串处理类
 * @author      sunkailun
 * @DateTime    2020/12/27  下午4:35
 * @email       376253703@qq.com
 * 
 */
public class StringUtils {

    /**
     * 元转换成分
     * @param amount
     * @return
     */
    public static String getMoney(String amount) {
        if(amount==null){
            return "";
        }
        // 金额转化为分为单位,处理包含, ￥ 或者$的金额
        String currency =  amount.replaceAll("\\$|\\￥|\\,", "");
        int index = currency.indexOf(".");
        int length = currency.length();
        Long amLong = 0L;
        if(index == -1){
            amLong = Long.valueOf(currency+"00");
        }else if(length - index >= 3){
            amLong = Long.valueOf((currency.substring(0, index+3)).replace(".", ""));
        }else if(length - index == 2){
            amLong = Long.valueOf((currency.substring(0, index+2)).replace(".", "")+0);
        }else{
            amLong = Long.valueOf((currency.substring(0, index+1)).replace(".", "")+"00");
        }
        return amLong.toString();
    }


    /**
     * 手机号隐藏
     * @author      sunkailun
     * @DateTime    2019/7/30  9:04 AM
     * @email       376253703@qq.com
     * 
     * @param mobile 手机号
     * @return      java.lang.String
     */
    public static  String getMobile(String mobile){
        return mobile.replaceAll("(?<=\\d{3})\\d(?=\\d{4})", "*");
    }

    /**
     * @param stringId 空白返回id
     * @param id       拼接id
     * @return String        返回类型
     * @Description: 执行内容:参数id追加
     * @Title: setId    方法名
     */
    public static String getJoining(String stringId, Integer id) {
        if (stringId == "" || stringId == null) {
            stringId += id;
        } else {
            stringId += "," + id;
        }
        return stringId;
    }

    /**
     * @param stringId 空白返回id
     * @param name       拼接id
     * @return String        返回类型
     * @Description: 执行内容:参数id追加
     * @Title: setId    方法名
     */
    public static String getJoining(String stringId, String name) {
        if (stringId == "" || stringId == null) {
            stringId += name;
        } else {
            stringId += "," + name;
        }
        return stringId;
    }

    /**
     * @param idIn      拼接的id
     * @return String        返回类型
     * @Description: 执行内容:in拼接'分号
     */
    public static String in(String idIn) {
        String [] i = idIn.split(",");
        String id = "";
        for (String s : i) {
            if (id == "" || id == null) {
                id += "'"+s+"'";
            } else {
                id += ",'"+s+"'";
            }
        }
        return id;
    }

    /**
     * list转string
     *
     * @param list
     * @return
     */
    public static String listToString(List list) {
        if(null ==list && list.size()<=0){
            return "";
        }else{
            StringBuilder sb = new StringBuilder();
            String resultString = "";
            for(int i=0;i<list.size();i++){
                if(i<list.size()-1){
                    sb.append(list.get(i));
                    sb.append(",");
                }else{
                    sb.append(list.get(i));
                }
            }
            resultString = sb.toString();
            return resultString;
        }
    }
}
