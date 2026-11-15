-- 新空库首次启动夹具：与各服务 V1__baseline.sql 一样含 Seata AT 回滚日志表（Apache Seata v2.6.0 PostgreSQL 版，时间列 TIMESTAMP(6)）。
CREATE TABLE undo_log (
    id SERIAL NOT NULL,
    branch_id BIGINT NOT NULL,
    xid VARCHAR(128) NOT NULL,
    context VARCHAR(128) NOT NULL,
    rollback_info BYTEA NOT NULL,
    log_status INT NOT NULL,
    log_created TIMESTAMP(6) NOT NULL,
    log_modified TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_undo_log PRIMARY KEY (id),
    CONSTRAINT ux_undo_log UNIQUE (xid, branch_id)
);
-- 按创建时间清理回滚日志用的索引。
CREATE INDEX ix_log_created ON undo_log(log_created);
-- 启动阶段经 Mapper 查询的业务表，只由本迁移创建。
CREATE TABLE fresh_probe (
    id varchar(64) PRIMARY KEY
);
