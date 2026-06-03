package com.kellen.config.xxljob;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-JOB执行器配置
 *
 * @author sunkailun
 * @DateTime 2026/6/3 11:40
 * @email 376253703@qq.com
 */
@ConfigurationProperties(prefix = "xxl.job")
public class XxlJobProperties {

    /**
     * 是否启用XXL-JOB执行器
     */
    private Boolean enabled = true;

    /**
     * 调度中心配置
     */
    private Admin admin = new Admin();

    /**
     * 执行器配置
     */
    private Executor executor = new Executor();

    /**
     * 调度中心访问令牌
     */
    private String accessToken;

    /**
     * 获取是否启用XXL-JOB执行器
     *
     * @return Boolean: 是否启用XXL-JOB执行器
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public Boolean getEnabled() {
        // 返回执行器总开关，允许微服务通过Nacos关闭任务注册。
        return enabled;
    }

    /**
     * 设置是否启用XXL-JOB执行器
     *
     * @param enabled: 是否启用XXL-JOB执行器
     * @return void
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public void setEnabled(Boolean enabled) {
        // 保存执行器总开关配置。
        this.enabled = enabled;
    }

    /**
     * 获取调度中心配置
     *
     * @return Admin: 调度中心配置
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public Admin getAdmin() {
        // 返回调度中心配置对象。
        return admin;
    }

    /**
     * 设置调度中心配置
     *
     * @param admin: 调度中心配置
     * @return void
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public void setAdmin(Admin admin) {
        // 保存调度中心配置对象。
        this.admin = admin;
    }

    /**
     * 获取执行器配置
     *
     * @return Executor: 执行器配置
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public Executor getExecutor() {
        // 返回执行器配置对象。
        return executor;
    }

    /**
     * 设置执行器配置
     *
     * @param executor: 执行器配置
     * @return void
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public void setExecutor(Executor executor) {
        // 保存执行器配置对象。
        this.executor = executor;
    }

    /**
     * 获取调度中心访问令牌
     *
     * @return String: 调度中心访问令牌
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public String getAccessToken() {
        // 返回调度中心访问令牌。
        return accessToken;
    }

    /**
     * 设置调度中心访问令牌
     *
     * @param accessToken: 调度中心访问令牌
     * @return void
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public void setAccessToken(String accessToken) {
        // 保存调度中心访问令牌。
        this.accessToken = accessToken;
    }

    /**
     * 调度中心配置
     *
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public static class Admin {

        /**
         * 调度中心地址，多个地址使用英文逗号分隔
         */
        private String addresses;

        /**
         * 获取调度中心地址
         *
         * @return String: 调度中心地址
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public String getAddresses() {
            // 返回调度中心地址。
            return addresses;
        }

        /**
         * 设置调度中心地址
         *
         * @param addresses: 调度中心地址
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setAddresses(String addresses) {
            // 保存调度中心地址。
            this.addresses = addresses;
        }
    }

    /**
     * 执行器配置
     *
     * @author sunkailun
     * @DateTime 2026/6/3 11:40
     * @email 376253703@qq.com
     */
    public static class Executor {

        /**
         * 执行器应用名，必须与XXL-JOB后台AppName一致
         */
        private String appname;

        /**
         * 执行器注册地址，通常为空，由ip和port组成
         */
        private String address;

        /**
         * 执行器对调度中心暴露的IP
         */
        private String ip;

        /**
         * 执行器对调度中心暴露的端口
         */
        private int port = 9999;

        /**
         * 执行器日志目录
         */
        private String logpath = "./logs/xxl-job/jobhandler";

        /**
         * 执行器日志保留天数
         */
        private int logretentiondays = 30;

        /**
         * 获取执行器应用名
         *
         * @return String: 执行器应用名
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public String getAppname() {
            // 返回执行器应用名。
            return appname;
        }

        /**
         * 设置执行器应用名
         *
         * @param appname: 执行器应用名
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setAppname(String appname) {
            // 保存执行器应用名。
            this.appname = appname;
        }

        /**
         * 获取执行器注册地址
         *
         * @return String: 执行器注册地址
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public String getAddress() {
            // 返回执行器注册地址。
            return address;
        }

        /**
         * 设置执行器注册地址
         *
         * @param address: 执行器注册地址
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setAddress(String address) {
            // 保存执行器注册地址。
            this.address = address;
        }

        /**
         * 获取执行器IP
         *
         * @return String: 执行器IP
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public String getIp() {
            // 返回执行器IP。
            return ip;
        }

        /**
         * 设置执行器IP
         *
         * @param ip: 执行器IP
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setIp(String ip) {
            // 保存执行器IP。
            this.ip = ip;
        }

        /**
         * 获取执行器端口
         *
         * @return int: 执行器端口
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public int getPort() {
            // 返回执行器端口。
            return port;
        }

        /**
         * 设置执行器端口
         *
         * @param port: 执行器端口
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setPort(int port) {
            // 保存执行器端口。
            this.port = port;
        }

        /**
         * 获取执行器日志目录
         *
         * @return String: 执行器日志目录
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public String getLogpath() {
            // 返回执行器日志目录。
            return logpath;
        }

        /**
         * 设置执行器日志目录
         *
         * @param logpath: 执行器日志目录
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setLogpath(String logpath) {
            // 保存执行器日志目录。
            this.logpath = logpath;
        }

        /**
         * 获取执行器日志保留天数
         *
         * @return int: 执行器日志保留天数
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public int getLogretentiondays() {
            // 返回执行器日志保留天数。
            return logretentiondays;
        }

        /**
         * 设置执行器日志保留天数
         *
         * @param logretentiondays: 执行器日志保留天数
         * @return void
         * @author sunkailun
         * @DateTime 2026/6/3 11:40
         * @email 376253703@qq.com
         */
        public void setLogretentiondays(int logretentiondays) {
            // 保存执行器日志保留天数。
            this.logretentiondays = logretentiondays;
        }
    }
}
