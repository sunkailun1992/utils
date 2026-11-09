package com.kellen.config.datasource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 源码脚本只支持可事务化 migration，不把普通字符串误识别成事务语句。 */
class PostgresqlDdlScriptPolicyTest {
    @Test
    void currentCommonResourceAndQuotedCommentAreAccepted() {
        assertThatCode(() -> PostgresqlDdlScriptPolicy.validate("db/postgresql/common-infra-schema.sql")).doesNotThrowAnyException();
        assertThatCode(() -> PostgresqlDdlScriptPolicy.validateSql("""
                /* outer /* nested */ comment */
                CREATE TABLE example (value text DEFAULT 'COMMIT;');
                COMMENT ON TABLE example IS 'it''s safe; ROLLBACK;';
                -- BEGIN;
                INSERT INTO example VALUES ('SYNTHETIC_SECRET');
                """)).doesNotThrowAnyException();
    }

    @Test
    void transactionSessionAndNonTransactionalStatementsAreRejected() {
        for (String sql : new String[]{"COMMIT;", "ROLLBACK;", "BEGIN;", "END;", "ABORT;", "START TRANSACTION;",
                "SET search_path=other;", "RESET ALL;", "CALL procedure();", "DO $$ BEGIN END $$;",
                "CREATE INDEX CONCURRENTLY example ON example(id);", "CREATE DATABASE other;", "VACUUM;",
                "CREATE TABLE example(id int); COMMIT;", "CREATE TABLE example(id int)",
                "-- @DELIMITER END\nCREATE TABLE example(id int);", "CREATE TABLE example(value text DEFAULT E'\\\\');"}) {
            assertThatThrownBy(() -> PostgresqlDdlScriptPolicy.validateSql(sql))
                    .isInstanceOf(IllegalStateException.class).hasMessage("PG_DDL_SOURCE_REJECTED").hasNoCause();
        }
    }

    @Test
    void sourceIdentityCannotUseFilesystemUrlTraversalOrCustomDelimiter() {
        for (String path : new String[]{"/tmp/secret.sql", "file:///tmp/secret.sql", "db/postgresql/../secret.sql",
                "db/postgresql/common-infra-schema.sql#;", "db/postgresql//schema.sql", "db/common-infra-schema.sql"}) {
            assertThatThrownBy(() -> PostgresqlDdlScriptPolicy.validate(path))
                    .isInstanceOf(IllegalStateException.class).hasMessage("PG_DDL_CLASSPATH_REQUIRED").hasNoCause();
        }
    }
}
