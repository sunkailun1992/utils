package com.kellen.utils.http;

import com.kellen.utils.enumeration.HttpType;
import com.kellen.utils.enumeration.HttpWay;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 按 OkHttp 官方用法验证 {@link OkHttpUtils#send}：用完即关闭响应、非 2xx 报错、表单按键值发送。
 */
class OkHttpUtilsResponseTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start(InetAddress.getByName("127.0.0.1"), 0);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void sendReturnsBodyText() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());

        String body = OkHttpUtils.send(new Request.Builder(), HttpWay.GET, url("/text"), null, HttpType.JSON);

        assertThat(body).isEqualTo("ok");
    }

    @Test
    void sendFailsOnNon2xx() {
        server.enqueue(new MockResponse.Builder().code(500).body("failed").build());

        assertThatThrownBy(() -> OkHttpUtils.send(new Request.Builder(), HttpWay.GET, url("/error"), null, HttpType.JSON))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("500");
    }

    @Test
    void formBodyUsesFormKeyValues() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body("done").build());

        OkHttpUtils.send(new Request.Builder(), HttpWay.POST, url("/form"), "{\"a\":\"1\"}", HttpType.FORM);

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getBody().utf8()).isEqualTo("a=1");
        assertThat(recorded.getHeaders().get("Content-Type")).startsWith("application/x-www-form-urlencoded");
    }

    private String url(String path) {
        return server.url(path).newBuilder().host("127.0.0.1").build().toString();
    }
}
