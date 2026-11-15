-- 启动顺序探针：MyBatis 在初始化阶段查询的表只由这一版迁移创建，Flyway 晚于 MyBatis 执行时启动即报表不存在。
CREATE TABLE ordering_probe (
    id varchar(64) PRIMARY KEY
);
