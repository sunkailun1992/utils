-- PostgreSQL-only common infrastructure; run in each selected personal business database before startup.
-- Historical MySQL scripts stay immutable and must not be executed against PostgreSQL.
-- Seata AT columns follow Apache Seata v2.6.0 script/client/at/db/postgresql.sql (Apache-2.0).
-- https://github.com/apache/incubator-seata/blob/v2.6.0/script/client/at/db/postgresql.sql
-- Preserve the existing MySQL DATETIME(6) precision; widening the official TIMESTAMP(0) does not change AT semantics.

CREATE TABLE IF NOT EXISTS public.ddl_history (
    script varchar(500) NOT NULL PRIMARY KEY,
    type varchar(30) NOT NULL,
    version varchar(30) NOT NULL
);
COMMENT ON TABLE public.ddl_history IS 'DDL 版本';
COMMENT ON COLUMN public.ddl_history.script IS '脚本';
COMMENT ON COLUMN public.ddl_history.type IS '类型';
COMMENT ON COLUMN public.ddl_history.version IS '版本';

CREATE TABLE IF NOT EXISTS public.undo_log (
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
CREATE INDEX IF NOT EXISTS ix_log_created ON public.undo_log(log_created);
COMMENT ON TABLE public.undo_log IS 'AT transaction mode undo table';
COMMENT ON COLUMN public.undo_log.branch_id IS 'branch transaction id';
COMMENT ON COLUMN public.undo_log.xid IS 'global transaction id';
COMMENT ON COLUMN public.undo_log.context IS 'undo_log context,such as serialization';
COMMENT ON COLUMN public.undo_log.rollback_info IS 'rollback info';
COMMENT ON COLUMN public.undo_log.log_status IS '0:normal status,1:defense status';
COMMENT ON COLUMN public.undo_log.log_created IS 'create datetime';
COMMENT ON COLUMN public.undo_log.log_modified IS 'modify datetime';
