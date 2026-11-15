package com.kellen.config.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 表结构改由各服务的 Flyway 迁移管理后，utils 不再带 MyBatis-Plus DDL 的运行器、公共建表脚本与自动配置。 */
class OldDdlModeRemovedTest {

    @Test
    void autoConfigurationNoLongerRegistersDdlRunner() {
        var candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates();
        assertThat(candidates).contains("com.kellen.config.json.JsonUtilAutoConfiguration")
                .doesNotContain("com.kellen.config.datasource.PostgresqlDdlAutoConfiguration");
    }

    @Test
    void oldDdlClassesAndScriptsAreGone() {
        assertThatThrownBy(() -> Class.forName("com.kellen.config.datasource.PostgresqlDdlApplicationRunner"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThat(getClass().getClassLoader().getResource("db/postgresql/common-infra-schema.sql")).isNull();
        assertThat(getClass().getClassLoader().getResource("db/common-infra-schema.sql")).isNull();
    }

    @Test
    void flywayAndPostgresqlModuleAreOnApiClasspath() {
        assertThatCode(() -> Class.forName("org.flywaydb.core.Flyway")).doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("org.flywaydb.database.postgresql.PostgreSQLDatabaseType"))
                .doesNotThrowAnyException();
    }
}
