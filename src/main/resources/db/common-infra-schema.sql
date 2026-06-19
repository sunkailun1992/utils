-- Common database infrastructure tables for services using MyBatis-Plus DDL and Seata AT.
-- Every business database that uses MysqlDdl must keep ddl_history.
-- Every business database that participates in Seata AT transactions must keep undo_log.

CREATE TABLE IF NOT EXISTS `ddl_history` (
    `script` varchar(500) NOT NULL COMMENT '脚本',
    `type` varchar(30) NOT NULL COMMENT '类型',
    `version` varchar(30) NOT NULL COMMENT '版本',
    PRIMARY KEY (`script`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='DDL 版本';

CREATE TABLE IF NOT EXISTS `undo_log` (
    `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
    `xid` varchar(128) NOT NULL COMMENT 'global transaction id',
    `context` varchar(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` longblob NOT NULL COMMENT 'rollback info',
    `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
    `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`),
    KEY `ix_log_created` (`log_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AT transaction mode undo table';
