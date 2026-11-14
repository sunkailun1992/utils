package com.kellen.utils.http;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证 {@link OkHttpUtils} 使用 JDK 默认的证书与主机名校验，且共享同一个线程安全的客户端。
 */
class OkHttpUtilsTlsTest {

    @Test
    void rejectsSelfSignedHttpsServer() throws Exception {
        HeldCertificate selfSigned = new HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build();
        HandshakeCertificates serverCertificates = new HandshakeCertificates.Builder().heldCertificate(selfSigned).build();
        try (MockWebServer server = new MockWebServer()) {
            server.useHttps(serverCertificates.sslSocketFactory());
            server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
            // 固定走 IPv4 回环，避免 localhost 先解析到 ::1 时报连接异常而掩盖证书校验结果。
            server.start(InetAddress.getByName("127.0.0.1"), 0);
            Request request = new Request.Builder().url(server.url("/secure").newBuilder().host("127.0.0.1").build()).build();

            assertThrows(SSLHandshakeException.class, () -> {
                try (Response ignored = OkHttpUtils.getOkHttpClient().newCall(request).execute()) {
                    // 能拿到响应说明信任了自签名证书。
                }
            });
        }
    }

    @Test
    void plainHttpRequestStillWorks() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse.Builder().code(200).body("plain").build());
            server.start();
            Request request = new Request.Builder().url(server.url("/plain")).build();

            try (Response response = OkHttpUtils.getOkHttpClient().newCall(request).execute()) {
                assertThat(response.code()).isEqualTo(200);
                assertThat(response.body().string()).isEqualTo("plain");
            }
        }
    }

    @Test
    void returnsSameClientAcrossThreads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<OkHttpClient>> calls = IntStream.range(0, 8)
                    .<Callable<OkHttpClient>>mapToObj(index -> OkHttpUtils::getOkHttpClient)
                    .toList();
            Set<OkHttpClient> clients = pool.invokeAll(calls).stream()
                    .map(OkHttpUtilsTlsTest::get)
                    .collect(Collectors.toSet());

            assertThat(clients).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static OkHttpClient get(Future<OkHttpClient> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
