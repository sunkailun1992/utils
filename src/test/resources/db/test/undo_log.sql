-- 测试夹具：Seata AT 回滚日志表，取自 Apache Seata v2.6.0 script/client/at/db/postgresql.sql（Apache-2.0），不带 schema 前缀，在测试连接的当前 schema 里创建。
-- https://github.com/apache/incubator-seata/blob/v2.6.0/script/client/at/db/postgresql.sql
-- 时间列沿用业务库现有的 TIMESTAMP(6) 精度；比官方 TIMESTAMP(0) 宽，不改变 AT 语义。服务的同名表在各自 V1__baseline.sql 里。
CREATE TABLE IF NOT EXISTS undo_log (
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
CREATE INDEX IF NOT EXISTS ix_log_created ON undo_log(log_created);
COMMENT ON TABLE undo_log IS 'AT transaction mode undo table';
COMMENT ON COLUMN undo_log.branch_id IS 'branch transaction id';
COMMENT ON COLUMN undo_log.xid IS 'global transaction id';
COMMENT ON COLUMN undo_log.context IS 'undo_log context,such as serialization';
COMMENT ON COLUMN undo_log.rollback_info IS 'rollback info';
COMMENT ON COLUMN undo_log.log_status IS '0:normal status,1:defense status';
COMMENT ON COLUMN undo_log.log_created IS 'create datetime';
COMMENT ON COLUMN undo_log.log_modified IS 'modify datetime';
