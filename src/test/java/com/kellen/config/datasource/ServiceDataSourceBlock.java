package com.kellen.config.datasource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 各服务 application.yml 统一的数据源块与 Nacos 里的地址账号键，供数据源与首次启动测试共用。 */
final class ServiceDataSourceBlock {

    /** 与服务 application.yml 相同的数据源块；地址、账号与口令在 Nacos。 */
    static final String[] PROPERTIES = {
            "spring.datasource.dynamic.primary=master",
            "spring.datasource.dynamic.strict=true",
            "spring.datasource.dynamic.seata=true",
            "spring.datasource.dynamic.seata-mode=AT",
            "spring.datasource.dynamic.druid.initial-size=1",
            "spring.datasource.dynamic.druid.min-idle=1",
            "spring.datasource.dynamic.druid.max-wait=3000",
            "spring.datasource.dynamic.druid.connect-timeout=3000",
            "spring.datasource.dynamic.druid.socket-timeout=30000",
            "spring.datasource.dynamic.druid.validation-query=SELECT 1",
            "spring.datasource.dynamic.druid.validation-query-timeout=3",
            "spring.datasource.dynamic.druid.test-on-borrow=true",
            "spring.datasource.dynamic.druid.test-while-idle=true",
            "spring.datasource.dynamic.druid.keep-alive=true",
            "spring.datasource.dynamic.druid.time-between-eviction-runs-millis=30000",
            "spring.datasource.dynamic.druid.keep-alive-between-time-millis=60000",
            "spring.datasource.dynamic.datasource.master.type=com.alibaba.druid.pool.DruidDataSource",
            "spring.datasource.dynamic.datasource.master.druid.max-active=4",
            "spring.datasource.dynamic.datasource.gray.type=com.alibaba.druid.pool.DruidDataSource",
            "spring.datasource.dynamic.datasource.gray.druid.max-active=2",
            "seata.enable-auto-data-source-proxy=false"
    };

    /** 各服务 application.yml 的 Flyway 块：连接引用 master 的同一组地址与账号。 */
    static final String[] FLYWAY = {
            "spring.flyway.baseline-on-migrate=true",
            "spring.flyway.baseline-version=1",
            "spring.flyway.validate-on-migrate=true",
            "spring.flyway.clean-disabled=true",
            "spring.flyway.url=${spring.datasource.dynamic.datasource.master.url}",
            "spring.flyway.user=${spring.datasource.dynamic.datasource.master.username}",
            "spring.flyway.password=${spring.datasource.dynamic.datasource.master.password}"
    };

    private ServiceDataSourceBlock() {
    }

    /** Nacos 里 master、gray 两组地址与账号；gray 与 master 同库。 */
    static String[] nacosKeys(String url, String username, String password) {
        List<String> keys = new ArrayList<>();
        for (String name : List.of("master", "gray")) {
            String prefix = "spring.datasource.dynamic.datasource." + name + ".";
            keys.add(prefix + "url=" + url);
            keys.add(prefix + "username=" + username);
            keys.add(prefix + "password=" + password);
            keys.add(prefix + "driver-class-name=org.postgresql.Driver");
        }
        return keys.toArray(String[]::new);
    }

    /** 把 {@code key=value} 列表转成 SpringApplication 默认属性。 */
    static Map<String, Object> asMap(String[]... groups) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String[] group : groups) {
            for (String entry : group) {
                int separator = entry.indexOf('=');
                properties.put(entry.substring(0, separator), entry.substring(separator + 1));
            }
        }
        return properties;
    }
}
