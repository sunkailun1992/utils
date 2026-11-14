package com.kellen.utils.http;

import cn.hutool.core.net.NetUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * IP 工具：客户端 IP 与内网判断直接使用 Hutool 的现成能力。
 *
 * @author 孙凯伦
 */
public final class IpUtils {

    private IpUtils() {
    }

    /**
     * 获取客户端 IP：依次读取 X-Forwarded-For、X-Real-IP 等转发头，多级代理取第一跳，
     * 都没有时取连接地址；本机回环地址统一返回 {@code 127.0.0.1}。
     *
     * @param request 当前请求
     * @return 客户端 IP
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = JakartaServletUtil.getClientIP(request);
        return "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip) ? "127.0.0.1" : ip;
    }

    /**
     * 获取本机局域网 IP。
     *
     * @return 本机 IP
     */
    public static String getLocalIp() {
        return NetUtil.getLocalhostStr();
    }

    /**
     * 判断是否为内网 IP（10.x、172.16–31.x、192.168.x 与 127.0.0.1）。
     *
     * @param ip IPv4 地址
     * @return 是内网 IP 时为 {@code true}
     */
    public static boolean isInnerIp(String ip) {
        return NetUtil.isInnerIP(ip);
    }
}
