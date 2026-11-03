import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/** 使用官方 SDK 在固定 github-dev 空间校验或 CAS 发布，不输出内容或凭据。 */
public class GithubNacosConfigSync {
    public static void main(String[] args) throws Exception {
        List<String> options = List.of(args);
        boolean publish = options.contains("--publish");
        Path previous = options.contains("--previous")
                ? Path.of(args[options.indexOf("--previous") + 1]) : null;
        String server = System.getenv("GITHUB_NACOS_SERVER_ADDR");
        if (server == null || server.isBlank()) throw new IllegalArgumentException("缺少 Nacos server 配置");
        Properties properties = new Properties();
        properties.setProperty("serverAddr", server);
        properties.setProperty("namespace", "github-dev");
        properties.setProperty("enableRemoteSyncConfig", "true");
        for (String key : List.of("username", "password")) {
            String value = System.getenv("GITHUB_NACOS_" + key.toUpperCase());
            if (value != null && !value.isBlank()) properties.setProperty(key, value);
        }
        ConfigService client = NacosFactory.createConfigService(properties);
        try (var stream = Files.list(Path.of(args[0]))) {
            for (Path file : stream.sorted().toList()) {
                String id = file.getFileName().toString();
                if (!id.endsWith(".yaml") && !id.endsWith(".yml")) continue;
                String expected = Files.readString(file);
                String before = client.getConfig(id, "DEFAULT_GROUP", 5000);
                if (!expected.equals(before)) {
                    if (!publish) throw new IllegalStateException("远端配置不一致：" + id);
                    boolean accepted;
                    if (before == null) {
                        accepted = client.publishConfig(id, "DEFAULT_GROUP", expected, "yaml");
                    } else {
                        if (previous == null || !before.equals(Files.readString(previous.resolve(id))))
                            throw new IllegalStateException("远端配置已变化或缺少已复核旧快照：" + id);
                        String md5 = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                                .digest(before.getBytes(StandardCharsets.UTF_8)));
                        accepted = client.publishConfigCas(id, "DEFAULT_GROUP", expected, md5, "yaml");
                    }
                    if (!accepted) throw new IllegalStateException("CAS 发布被拒绝：" + id);
                }
                String actual = null;
                for (int attempt = 0; attempt < 10; attempt++) {
                    actual = client.getConfig(id, "DEFAULT_GROUP", 5000);
                    if (expected.equals(actual)) break;
                    Thread.sleep(200);
                }
                if (!expected.equals(actual)) throw new IllegalStateException("读回不一致：" + id);
                System.out.println("verified github-dev/DEFAULT_GROUP/" + id);
            }
        } finally {
            client.shutDown();
        }
    }
}
