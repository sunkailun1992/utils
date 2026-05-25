package com.kellen.utils;

import cn.hutool.core.convert.Convert;
import com.kellen.utils.enumeration.HttpType;
import com.kellen.utils.enumeration.HttpWay;
import okhttp3.Request;

import java.io.UnsupportedEncodingException;
import java.util.Map;


/**
 *
 * @author: 孙凯伦
 *
 * @email: 376253703@qq.com
 * @description: 根据IP地址获取详细的地域信息
 * @date: 2022/1/18 10:23 AM
 *
 */
public class AddressUtils {
    /**
     * @param ip 请求的参数
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getAddresses(String ip) {
        // 这里调用百度API
        String urlStr = "http://api.map.baidu.com/location/ip?";
        //1、组织请求头
        Request.Builder builder = new Request.Builder();
        //返回内容
        String returnStr = null;
        try {
            //请求
            returnStr = OkhttpUtils.send(builder, HttpWay.POST, urlStr + "ip=" + ip + "&ak=vmhRnIi0bhUSMrhZnbgG4Y0fS8C8Kb8i&coor=bd09ll", "", HttpType.JSON).string();
            //编码转换
            returnStr = decodeUnicode(returnStr);
            //返回内容
            Map<String, Object> map = JsonUtil.bean(returnStr, Map.class);
            //返回地址
            return Convert.toStr(map.get("address"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * unicode 转换成 中文
     *
     * @param theString
     * @return
     * @author fanhui 2007-3-15
     */
    public static String decodeUnicode(String theString) {
        char aChar;
        int len = theString.length();
        StringBuffer outBuffer = new StringBuffer(len);
        for (int x = 0; x < len; ) {
            aChar = theString.charAt(x++);
            if (aChar == '\\') {
                aChar = theString.charAt(x++);
                if (aChar == 'u') {
                    int value = 0;
                    for (int i = 0; i < 4; i++) {
                        aChar = theString.charAt(x++);
                        switch (aChar) {
                            case '0':
                            case '1':
                            case '2':
                            case '3':
                            case '4':
                            case '5':
                            case '6':
                            case '7':
                            case '8':
                            case '9':
                                value = (value << 4) + aChar - '0';
                                break;
                            case 'a':
                            case 'b':
                            case 'c':
                            case 'd':
                            case 'e':
                            case 'f':
                                value = (value << 4) + 10 + aChar - 'a';
                                break;
                            case 'A':
                            case 'B':
                            case 'C':
                            case 'D':
                            case 'E':
                            case 'F':
                                value = (value << 4) + 10 + aChar - 'A';
                                break;
                            default:
                                throw new IllegalArgumentException(
                                        "Malformed      encoding.");
                        }
                    }
                    outBuffer.append((char) value);
                } else {
                    if (aChar == 't') {
                        aChar = '\t';
                    } else if (aChar == 'r') {
                        aChar = '\r';
                    } else if (aChar == 'n') {
                        aChar = '\n';
                    } else if (aChar == 'f') {
                        aChar = '\f';
                    }
                    outBuffer.append(aChar);
                }
            } else {
                outBuffer.append(aChar);
            }
        }
        return outBuffer.toString();
    }
}

