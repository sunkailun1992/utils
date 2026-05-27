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
        DruidDataSource druidDataSource = new DruidDataSource(); // 创建 Druid 数据源实例。
        druidDataSource.setUrl(url); // 设置 JDBC URL。
        druidDataSource.setUsername(username); // 设置数据库用户名。
        druidDataSource.setPassword(password); // 设置数据库密码。
        druidDataSource.setDriverClassName(driverClassName); // 设置数据库驱动类名。
        return druidDataSource; // 返回主库数据源。
    }


    /**
     * 创建灰度库数据源。
     *
     * @return 灰度库数据源
     */
    @Bean(name = "gray")
    public DataSource gray() {
        DruidDataSource druidDataSource = new DruidDataSource(); // 创建 Druid 数据源实例。
        druidDataSource.setUrl(urlGray); // 设置 JDBC URL。
        druidDataSource.setUsername(usernameGray); // 设置数据库用户名。
        druidDataSource.setPassword(passwordGray); // 设置数据库密码。
        druidDataSource.setDriverClassName(driverClassNameGray); // 设置数据库驱动类名。
        return druidDataSource; // 返回灰度库数据源。
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
