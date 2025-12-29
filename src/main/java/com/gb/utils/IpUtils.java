package com.gb.utils;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ip工具类
 * @author sunkailun
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
        String ipAddress = request.getHeader("x-forwarded-for");
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
            if ("127.0.0.1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
                //根据网卡取本机配置的IP
                InetAddress inet = null;
                try {
                    inet = InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
                ipAddress = inet.getHostAddress();
            }
        }
        //对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割  "***.***.***.***".length() = 15
        if (ipAddress != null && ipAddress.length() > 15) {
            if (ipAddress.indexOf(",") > 0) {
                ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
            }
        }
        return ipAddress;
    }

    /**
     * 获得服务器ip
     * @author      sunkailun
     * @DateTime    2020/7/27  12:03 下午
     * @email       376253703@qq.com
     * 
     * @param :
     * @return      java.lang.String
     */
    public static String getIp() {
        try {
            InetAddress ia = InetAddress.getLocalHost();
            String localip = ia.getHostAddress();
            return localip;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "500";
        }
    }

    /**
     * 本地地址判断
     * @author      sunkailun
     * @DateTime    2018/8/18  下午1:04
     * @email       376253703@qq.com
     * 
     * @param ip:
     * @return      java.lang.Boolean
     */
    public static Boolean localAddressIp(String ip) {
        String[] i = ip.split("\\.");
        if (i.length > 0) {
            if ("192".equals(i[0])) {
                if ("168".equals(i[1])) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }else{
            return false;
        }
    }
}
