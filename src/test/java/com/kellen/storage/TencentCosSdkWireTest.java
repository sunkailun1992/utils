package com.kellen.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.endpoint.EndpointBuilder;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import com.sun.net.httpserver.HttpServer;
import org.dromara.x.file.storage.core.FileStorageProperties;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.FileStorageServiceBuilder;
import org.dromara.x.file.storage.core.platform.FileStorageClientFactory;
import org.dromara.x.file.storage.core.platform.TencentCosFileStorage;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 生产 XFS/COS SDK 对接回环 HTTP；显式 endpointBuilder，绝不访问真实云。 */
class TencentCosSdkWireTest {
    @Test void nativeCosSdkHeadAndPresignedGetUseExactStoredPlatformAndObjectKey() throws Exception {
        byte[] payload = "synthetic-cos-file".getBytes(StandardCharsets.UTF_8);
        var paths = new CopyOnWriteArrayList<String>();
        var methods = new CopyOnWriteArrayList<String>();
        var attempts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            attempts.incrementAndGet(); paths.add(exchange.getRequestURI().getPath()); methods.add(exchange.getRequestMethod());
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.getResponseHeaders().set("ETag", "\"synthetic-etag\"");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(payload.length));
            if ("PUT".equals(exchange.getRequestMethod())) {
                byte[] incoming = exchange.getRequestBody().readAllBytes();
                if (!java.util.Arrays.equals(incoming, payload)) {
                    exchange.sendResponseHeaders(400, -1); exchange.close(); return;
                }
                exchange.getResponseHeaders().remove("Content-Length");
                exchange.sendResponseHeaders(200, -1);
            } else if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            }
            exchange.close();
        });
        server.start();
        String authority = "127.0.0.1:" + server.getAddress().getPort();
        var options = new ClientConfig(new Region("ap-singapore"));
        options.setHttpProtocol(HttpProtocol.http); options.setMaxErrorRetry(0); options.setChangeEndpointRetry(false);
        options.setEndpointBuilder(new EndpointBuilder() {
            @Override public String buildGeneralApiEndpoint(String bucket) { return authority; }
            @Override public String buildGetServiceApiEndpoint() { return authority; }
        });
        COSClient client = new COSClient(new BasicCOSCredentials("synthetic-access", "synthetic-secret"), options);
        try {
            var config = new FileStorageProperties.TencentCosConfig(); config.setPlatform("wire-cos");
            config.setRegion("ap-singapore"); config.setBucketName("synthetic-1250000000");
            config.setSecretId("synthetic-access"); config.setSecretKey("synthetic-secret"); config.setBasePath("");
            FileStorageClientFactory<COSClient> factory = new FileStorageClientFactory<>() {
                @Override public String getPlatform() { return "wire-cos"; }
                @Override public COSClient getClient() { return client; }
                @Override public void close() { }
            };
            var properties = new FileStorageProperties(); properties.setDefaultPlatform("wire-cos");
            properties.setTencentCos(List.of(config));
            var builder = FileStorageServiceBuilder.create(properties).useDefault();
            builder.getClientFactoryList().add(List.of(factory));
            var files = builder.build();
            var storage = new PrivateObjectStorageService(files);
            String objectKey = "github/owner-2/file.bin";
            storage.putObject(objectKey, "application/octet-stream", payload.length, new ByteArrayInputStream(payload));
            storage.verifyObjectExists("wire-cos", objectKey);
            var uri = storage.signedViewUri("wire-cos", objectKey, 60);
            assertThat(uri.getHost()).isEqualTo("127.0.0.1");
            assertThat(uri.getPort()).isEqualTo(server.getAddress().getPort());
            assertThat(uri.getPath()).isEqualTo("/" + objectKey);
            assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
                    .contains("q-signature=").doesNotContain("synthetic-secret");
            var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
            assertThat(paths).containsOnly("/" + objectKey);
            assertThat(methods).containsExactly("PUT", "HEAD", "GET");
            assertThat(attempts).hasValue(3);
        } finally {
            client.shutdown(); server.stop(0);
        }
    }
}
