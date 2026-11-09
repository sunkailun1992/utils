package com.kellen.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 保证 PostgreSQL 秒单位选项不会被 Druid 当作毫秒覆盖物理连接。 */
class PostgresqlDruidTimeoutTest {
    private static final String BASE = "jdbc:postgresql://127.0.0.1:1/synthetic_fixture";

    @Test
    void convertsSecondsBeforeDruidInitAndPreservesOtherOptionsVerbatim() throws Exception {
        String url = BASE + "?ApplicationName=fixture%20probe&connectTimeout=3&tcpKeepAlive=true"
                + "&socketTimeout=30&sslmode=verify-full&options=-c%20statement_timeout%3D5000";
        try (DruidDataSource pool = configuration(url).master()) {
            assertThat(pool.getUrl()).isEqualTo(BASE + "?ApplicationName=fixture%20probe&tcpKeepAlive=true"
                    + "&sslmode=verify-full&options=-c%20statement_timeout%3D5000");
            assertTimeoutsSurviveInitialization(pool, 3000, 30000);
        }
    }

    @Test
    void omittedTimeoutOptionsKeepTheOriginalThreeAndThirtySecondBudgets() throws Exception {
        try (DruidDataSource pool = configuration(BASE).master()) {
            assertThat(pool.getUrl()).isEqualTo(BASE);
            assertTimeoutsSurviveInitialization(pool, 3000, 30000);
        }
    }

    @Test
    void grayPoolUsesTheSameTimeoutNormalizationAndKeepsExplicitFiniteValues() throws Exception {
        DynamicDataSourceConfig config = configuration(BASE);
        ReflectionTestUtils.setField(config, "urlGray", BASE + "?socketTimeout=41&connectTimeout=7");
        ReflectionTestUtils.setField(config, "usernameGray", "fixture");
        ReflectionTestUtils.setField(config, "passwordGray", "synthetic-fixture");
        ReflectionTestUtils.setField(config, "driverClassNameGray", "org.postgresql.Driver");
        try (DruidDataSource pool = (DruidDataSource) config.gray()) {
            assertThat(pool.getUrl()).isEqualTo(BASE);
            assertTimeoutsSurviveInitialization(pool, 7000, 41000);
        }
    }

    @Test
    void encodedTimeoutOptionsAreRemovedRatherThanReachingDruidAgain() throws Exception {
        try (DruidDataSource pool = configuration(BASE + "?%73ocketTimeout=3%30&connect%54imeout=3&x=a%26b").master()) {
            assertThat(pool.getUrl()).isEqualTo(BASE + "?x=a%26b");
            assertTimeoutsSurviveInitialization(pool, 3000, 30000);
        }
    }

    @Test
    void independentlyOmittedOptionsUseTheirOwnDefault() throws Exception {
        try (DruidDataSource pool = configuration(BASE + "?connectTimeout=8&tcpKeepAlive=true").master()) {
            assertThat(pool.getUrl()).isEqualTo(BASE + "?tcpKeepAlive=true");
            assertTimeoutsSurviveInitialization(pool, 8000, 30000);
        }
        try (DruidDataSource pool = configuration(BASE + "?socketTimeout=9").master()) {
            assertTimeoutsSurviveInitialization(pool, 3000, 9000);
        }
    }

    @Test
    void largestFiniteMillisecondsDoNotOverflow() throws Exception {
        try (DruidDataSource pool = configuration(BASE + "?socketTimeout=2147483&connectTimeout=2147483").master()) {
            assertTimeoutsSurviveInitialization(pool, 2147483000, 2147483000);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "socketTimeout=0", "connectTimeout=0", "socketTimeout=-1", "connectTimeout=-1",
            "socketTimeout=1.5", "connectTimeout=1e3", "socketTimeout=", "connectTimeout",
            "socketTimeout=2147484", "connectTimeout=9999999999999999999999999",
            "socketTimeout=30&socketTimeout=30", "connectTimeout=3&connectTimeout=4",
            "socketTimeout=30&%73ocketTimeout=30", "SocketTimeout=30", "connecttimeout=3",
            "socketTimeout=%20", "socketTimeout=+3", "socketTimeout=%ZZ", "socketTimeout=03",
            "socketTimeout=synthetic-secret-not-in-errors"
    })
    void rejectsAmbiguousUnboundedOrInvalidTimeoutsWithoutEchoingUrl(String options) {
        assertThatThrownBy(() -> configuration(BASE + "?" + options).master())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining("synthetic-secret")
                .hasMessageNotContaining(options);
    }

    private DynamicDataSourceConfig configuration(String url) {
        DynamicDataSourceConfig config = new DynamicDataSourceConfig();
        ReflectionTestUtils.setField(config, "url", url);
        ReflectionTestUtils.setField(config, "username", "fixture");
        ReflectionTestUtils.setField(config, "password", "synthetic-fixture");
        ReflectionTestUtils.setField(config, "driverClassName", "org.postgresql.Driver");
        return config;
    }

    private void assertTimeoutsSurviveInitialization(DruidDataSource pool, int connect, int socket) throws Exception {
        assertThat(pool.getConnectTimeout()).isEqualTo(connect);
        assertThat(pool.getSocketTimeout()).isEqualTo(socket);
        pool.setInitialSize(0);
        pool.setMinIdle(0);
        pool.init();
        assertThat(pool.getConnectTimeout()).isEqualTo(connect);
        assertThat(pool.getSocketTimeout()).isEqualTo(socket);
    }
}
