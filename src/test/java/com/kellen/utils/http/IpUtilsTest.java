package com.kellen.utils.http;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证客户端 IP 取值与内网判断按 Hutool 的主流写法：转发头取第一跳，回环地址统一为 127.0.0.1。
 */
class IpUtilsTest {

    @Test
    void innerIpRangesAreRecognized() {
        assertThat(IpUtils.isInnerIp("10.1.2.3")).isTrue();
        assertThat(IpUtils.isInnerIp("172.16.0.1")).isTrue();
        assertThat(IpUtils.isInnerIp("192.168.1.1")).isTrue();
        assertThat(IpUtils.isInnerIp("8.8.8.8")).isFalse();
    }

    @Test
    void loopbackClientBecomes127() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("0:0:0:0:0:0:0:1");

        assertThat(IpUtils.getClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void forwardedForFirstHopIsUsed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");

        assertThat(IpUtils.getClientIp(request)).isEqualTo("203.0.113.7");
    }
}
