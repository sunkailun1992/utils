package com.kellen.utils.http;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * ip工具类
 * @author 孙凯伦
 * @DateTime 2020/12/27  下午4:32
 * @email 376253703@qq.com
 * 
 * @explain
 */
public class IpUtils {

    /**
     * 获取用户实际ip
     *
     * @param request
     * @return
     */
    public static String getIp(HttpServletRequest request) {
        String ipAddress = JakartaServletUtil.getClientIP(request, "x-forwarded-for", "Proxy-Client-IP", "WL-Proxy-Client-IP");
        if (StrUtil.equalsAny(ipAddress, "127.0.0.1", "0:0:0:0:0:0:0:1")) {
            return NetUtil.getLocalhostStr();
        }
        return NetUtil.getMultistageReverseProxyIp(ipAddress);
    }

    /**
     * 获得服务器ip
     * @author 孙凯伦
     * @DateTime    2020/7/27  12:03 下午
     * @email       376253703@qq.com
     * 
     * @param :
     * @return      java.lang.String
     */
    public static String getIp() {
        return NetUtil.getLocalhostStr();
    }

    /**
     * 本地地址判断
     * @author 孙凯伦
     * @DateTime    2018/8/18  下午1:04
     * @email       376253703@qq.com
     * 
     * @param ip:
     * @return      java.lang.Boolean
     */
    public static Boolean localAddressIp(String ip) {
        return StrUtil.startWith(ip, "192.168.");
    }
}
