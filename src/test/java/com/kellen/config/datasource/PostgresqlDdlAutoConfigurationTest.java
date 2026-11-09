package com.kellen.config.datasource;

import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.DdlAutoConfiguration;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** 真正配置解析阶段的注册顺序，不能只手工 new runner 证明默认自动配置会退让。 */
class PostgresqlDdlAutoConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DdlAutoConfiguration.class, PostgresqlDdlAutoConfiguration.class));

    @Test
    void userDdlIsVisibleBeforeAutoConfigurationAndOfficialRunnerBacksOff() {
        context.withUserConfiguration(BusinessConfiguration.class).run(application -> {
            assertThat(application).hasSingleBean(IDdl.class).hasSingleBean(DdlApplicationRunner.class);
            assertThat(application.getBean(DdlApplicationRunner.class)).isInstanceOf(PostgresqlDdlApplicationRunner.class);
            assertThat(application).doesNotHaveBean("ddlApplicationRunner");
        });
    }

    @Test
    void bootImportsDiscoversRunnerWithoutComponentScan() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()).getCandidates())
                .contains(PostgresqlDdlAutoConfiguration.class.getName());
    }

    @Test
    void absentBusinessDdlDoesNotCreateRunner() {
        context.run(application -> assertThat(application).doesNotHaveBean(DdlApplicationRunner.class));
    }

    @Test
    void explicitlyOwnedRunnerIsPreserved() {
        context.withUserConfiguration(BusinessConfiguration.class)
                .withBean("ownedRunner", DdlApplicationRunner.class, () -> new DdlApplicationRunner(List.of()))
                .run(application -> assertThat(application).hasSingleBean(DdlApplicationRunner.class)
                        .hasBean("ownedRunner").doesNotHaveBean("postgresqlDdlApplicationRunner"));
    }

    @Configuration(proxyBeanMethods = false)
    static class BusinessConfiguration {
        @Bean
        IDdl businessDdl() {
            return new IDdl() {
                @Override public void runScript(Consumer<DataSource> consumer) { }
                @Override public List<String> getSqlFiles() { return List.of(); }
            };
        }
    }
}
