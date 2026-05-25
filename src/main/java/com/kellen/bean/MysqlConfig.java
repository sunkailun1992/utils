package com.kellen.bean;

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
 * @ClassName DynamicDataSourceConfiguration
 * @Description 动态数据源配置
 * @Author 孙凯伦
 * @Email 376253703@qq.com
 * @Time 2021/6/29 11:25 上午
 */
@Configuration
public class MysqlConfig {

    /**
     * mysql默认
     */
    @Value("${mysql.url}")
    private String url;
    @Value("${mysql.username}")
    private String username;
    @Value("${mysql.password}")
    private String password;
    @Value("${mysql.driverClassName}")
    private String driverClassName;


    /**
     * mysql灰度
     */
    @Value("${mysql-gray.url}")
    private String urlGray;
    @Value("${mysql-gray.username}")
    private String usernameGray;
    @Value("${mysql-gray.password}")
    private String passwordGray;
    @Value("${mysql-gray.driverClassName}")
    private String driverClassNameGray;


    /**
     * mysql银行
     */
    @Value("${mysql-bank.url}")
    private String urlBank;
    @Value("${mysql-bank.username}")
    private String usernameBank;
    @Value("${mysql-bank.password}")
    private String passwordBank;
    @Value("${mysql-bank.driverClassName}")
    private String driverClassNameBank;


    /**
     * mysql监管
     */
    @Value("${mysql-hx.url}")
    private String urlHx;
    @Value("${mysql-hx.username}")
    private String usernameHx;
    @Value("${mysql-hx.password}")
    private String passwordHx;
    @Value("${mysql-hx.driverClassName}")
    private String driverClassNameHx;


    /**
     * mysql监管
     */
    @Value("${mysql-jghx.url}")
    private String urlJgHx;
    @Value("${mysql-jghx.username}")
    private String usernameJgHx;
    @Value("${mysql-jghx.password}")
    private String passwordJgHx;
    @Value("${mysql-jghx.driverClassName}")
    private String driverClassNameJgHx;


    /**
     * mysql监管
     */
    @Value("${mysql-hz.url}")
    private String urlHz;
    @Value("${mysql-hz.username}")
    private String usernameHz;
    @Value("${mysql-hz.password}")
    private String passwordHz;
    @Value("${mysql-hz.driverClassName}")
    private String driverClassNameHz;


    /**
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: primary
     * @description: TODO  默认数据源
     * @return: com.alibaba.druid.pool.DruidDataSource
     * @date: 2021/6/29 11:23 上午
     */
    @Bean(name = "master")
    public DruidDataSource master() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(url);
        druidDataSource.setUsername(username);
        druidDataSource.setPassword(password);
        druidDataSource.setDriverClassName(driverClassName);
        return druidDataSource;
    }


    /**
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: gray
     * @description: TODO  灰度数据源
     * @return: javax.sql.DataSource
     * @date: 2021/6/29 11:23 上午
     */
    @Bean(name = "gray")
    public DataSource gray() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(urlGray);
        druidDataSource.setUsername(usernameGray);
        druidDataSource.setPassword(passwordGray);
        druidDataSource.setDriverClassName(driverClassNameGray);
        return druidDataSource;
    }


    /**
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: gray
     * @description: TODO  银行数据源
     * @return: javax.sql.DataSource
     * @date: 2021/6/29 11:23 上午
     */
    @Bean(name = "bank")
    public DataSource bank() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(urlBank);
        druidDataSource.setUsername(usernameBank);
        druidDataSource.setPassword(passwordBank);
        druidDataSource.setDriverClassName(driverClassNameBank);
        return druidDataSource;
    }


    /**
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: gray
     * @description: TODO  监管数据源
     * @return: javax.sql.DataSource
     * @date: 2021/6/29 11:23 上午
     */
    @Bean(name = "hx")
    public DataSource hx() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(urlHx);
        druidDataSource.setUsername(usernameHx);
        druidDataSource.setPassword(passwordHx);
        druidDataSource.setDriverClassName(driverClassNameHx);
        return druidDataSource;
    }


    /**
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: gray
     * @description: TODO  监管数据源
     * @return: javax.sql.DataSource
     * @date: 2021/6/29 11:23 上午
     */
    @Bean(name = "jghx")
    public DataSource jghx() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(urlJgHx);
        druidDataSource.setUsername(usernameJgHx);
        druidDataSource.setPassword(passwordJgHx);
        druidDataSource.setDriverClassName(driverClassNameJgHx);
        return druidDataSource;
    }


    /**
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: gray
     * @description: TODO  汇中多数据源
     * @return: javax.sql.DataSource
     * @date: 2021/6/29 11:23 上午
     */
    @Bean(name = "hz")
    public DataSource hz() {
        DruidDataSource druidDataSource = new DruidDataSource();
        druidDataSource.setUrl(urlHz);
        druidDataSource.setUsername(usernameHz);
        druidDataSource.setPassword(passwordHz);
        druidDataSource.setDriverClassName(driverClassNameHz);
        return druidDataSource;
    }


    /**
     * @param primary
     * @param gray
     * @auther: 孙凯伦
     * @email: 376253703@qq.com
     * @name: multipleDataSource
     * @description: TODO  设置多数据源
     * @return: javax.sql.DataSource
     * @date: 2021/6/29 11:21 上午
     */
    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("master") DataSource primary, @Qualifier("gray") DataSource gray, @Qualifier("bank") DataSource bank, @Qualifier("hx") DataSource hx, @Qualifier("jghx") DataSource jghx, @Qualifier("hz") DataSource hz) {
        //数据源集合
        DynamicDataSourceProvider provider = () -> {
            Map<String, DataSource> dataSourceMap = new LinkedHashMap<>();
            dataSourceMap.put("master", new DataSourceProxy(primary));
            dataSourceMap.put("gray", new DataSourceProxy(gray));
            dataSourceMap.put("bank", new DataSourceProxy(bank));
            dataSourceMap.put("hx", new DataSourceProxy(hx));
            dataSourceMap.put("jghx", new DataSourceProxy(jghx));
            dataSourceMap.put("hz", new DataSourceProxy(hz));
            return dataSourceMap;
        };
        DynamicRoutingDataSource dataSource = new DynamicRoutingDataSource(Collections.singletonList(provider));
        dataSource.setPrimary("master");
        return dataSource;
    }

}
