package com.kellen.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.provider.DynamicDataSourceProvider;
import io.seata.rm.datasource.DataSourceProxy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态数据源配置。
 *
 * @author 孙凯伦
 */
@Configuration
public class DynamicDataSourceConfig {

    /**
     * 连接有效性检查 SQL。
     */
    @Value("${utils.datasource.health.validation-query:SELECT 1}")
    private String validationQuery = "SELECT 1";

    /**
     * 连接有效性检查超时秒数。
     */
    @Value("${utils.datasource.health.validation-query-timeout:3}")
    private int validationQueryTimeout = 3;

    /**
     * 是否在连接借出前检查有效性。
     */
    @Value("${utils.datasource.health.test-on-borrow:true}")
    private boolean testOnBorrow = true;

    /**
     * 是否在空闲检测时检查连接有效性。
     */
    @Value("${utils.datasource.health.test-while-idle:true}")
    private boolean testWhileIdle = true;

    /**
     * 是否保活最小空闲连接。
     */
    @Value("${utils.datasource.health.keep-alive:true}")
    private boolean keepAlive = true;

    /**
     * 连接池空闲检测间隔毫秒数。
     */
    @Value("${utils.datasource.health.eviction-run-interval-millis:30000}")
    private long evictionRunIntervalMillis = 30_000L;

    /**
     * 空闲连接保活间隔毫秒数。
     */
    @Value("${utils.datasource.health.keep-alive-interval-millis:60000}")
    private long keepAliveIntervalMillis = 60_000L;

    /**
     * 每个数据源保留的最小空闲连接数。
     */
    @Value("${utils.datasource.health.min-idle:1}")
    private int minIdle = 1;

    /**
     * 主库 JDBC URL。
     */
    @Value("${mysql.url}")
    private String url;

    /**
     * 主库用户名。
     */
    @Value("${mysql.username}")
    private String username;

    /**
     * 主库密码。
     */
    @Value("${mysql.password}")
    private String password;

    /**
     * 主库驱动类名。
     */
    @Value("${mysql.driverClassName}")
    private String driverClassName;


    /**
     * 灰度库 JDBC URL。
     */
    @Value("${mysql-gray.url}")
    private String urlGray;

    /**
     * 灰度库用户名。
     */
    @Value("${mysql-gray.username}")
    private String usernameGray;

    /**
     * 灰度库密码。
     */
    @Value("${mysql-gray.password}")
    private String passwordGray;

    /**
     * 灰度库驱动类名。
     */
    @Value("${mysql-gray.driverClassName}")
    private String driverClassNameGray;

    /**
     * 创建主库数据源。
     *
     * @return 主库 Druid 数据源
     */
    @Bean(name = "master")
    public DruidDataSource master() {
        return createDruidDataSource(url, username, password, driverClassName);
    }


    /**
     * 创建灰度库数据源。
     *
     * @return 灰度库数据源
     */
    @Bean(name = "gray")
    public DataSource gray() {
        return createDruidDataSource(urlGray, usernameGray, passwordGray, driverClassNameGray);
    }

    /**
     * 创建具备失效连接检测与空闲保活能力的 Druid 数据源。
     *
     * <p>数据库可能按 {@code wait_timeout} 主动关闭长期空闲连接。借出前校验负责淘汰
     * 已失效连接，空闲检测与保活负责降低首次业务请求命中坏连接的概率。</p>
     *
     * @param jdbcUrl        JDBC 地址
     * @param jdbcUsername   数据库用户名
     * @param jdbcPassword   数据库密码
     * @param jdbcDriverName JDBC 驱动类名
     * @return 已配置连接健康检查的 Druid 数据源
     */
    private DruidDataSource createDruidDataSource(String jdbcUrl,
                                                   String jdbcUsername,
                                                   String jdbcPassword,
                                                   String jdbcDriverName) {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(jdbcUrl);
        druidDataSource.setUsername(jdbcUsername);
        druidDataSource.setPassword(jdbcPassword);
        druidDataSource.setDriverClassName(jdbcDriverName);

        druidDataSource.setValidationQuery(validationQuery);
        druidDataSource.setValidationQueryTimeout(validationQueryTimeout);
        druidDataSource.setTestOnBorrow(testOnBorrow);
        druidDataSource.setTestWhileIdle(testWhileIdle);
        druidDataSource.setKeepAlive(keepAlive);
        long safeEvictionRunIntervalMillis = Math.max(1L, evictionRunIntervalMillis);
        druidDataSource.setTimeBetweenEvictionRunsMillis(safeEvictionRunIntervalMillis);
        druidDataSource.setKeepAliveBetweenTimeMillis(
                normalizeKeepAliveInterval(safeEvictionRunIntervalMillis, keepAliveIntervalMillis));
        druidDataSource.setMinIdle(minIdle);
        return druidDataSource;
    }

    /**
     * 保证 Druid 保活间隔严格大于空闲检测间隔。
     *
     * <p>Druid 初始化时会拒绝相等或更小的组合。这里同时修正默认值和外部误配置，
     * 避免公共配置升级后所有消费者在创建数据源阶段直接启动失败。</p>
     *
     * @param evictionIntervalMillis 空闲检测间隔
     * @param configuredKeepAliveIntervalMillis 配置的保活间隔
     * @return 可被 Druid 接受的保活间隔
     */
    private long normalizeKeepAliveInterval(long evictionIntervalMillis,
                                            long configuredKeepAliveIntervalMillis) {
        if (configuredKeepAliveIntervalMillis > evictionIntervalMillis) {
            return configuredKeepAliveIntervalMillis;
        }
        long increment = Math.max(1_000L, Math.min(evictionIntervalMillis, 60_000L));
        if (evictionIntervalMillis > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return evictionIntervalMillis + increment;
    }

    /**
     * 创建动态路由数据源。
     *
     * @param primary 主库数据源
     * @param gray    灰度库数据源
     * @return 动态路由数据源
     */
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("master") DataSource primary, @Qualifier("gray") DataSource gray) {
        DynamicDataSourceProvider provider = () -> {
            Map<String, DataSource> dataSourceMap = new LinkedHashMap<>(); // 使用有序 Map 保持数据源注册顺序稳定。
            dataSourceMap.put("master", new DataSourceProxy(primary)); // 注册主库并接入 Seata 代理。
            dataSourceMap.put("gray", new DataSourceProxy(gray)); // 注册灰度库并接入 Seata 代理。
            return dataSourceMap; // 返回 dynamic-datasource 所需的数据源集合。
        };
        DynamicRoutingDataSource dataSource = new DynamicRoutingDataSource(Collections.singletonList(provider)); // 创建动态路由数据源。
        dataSource.setPrimary("master"); // 设置默认主库。
        return dataSource; // 返回 Spring 主数据源。
    }

}
