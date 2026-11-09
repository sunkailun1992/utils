package com.kellen.config.datasource;

import org.apache.ibatis.io.Resources;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** 当前受信 PG classpath migration 的有限语法合同，不是任意 SQL 的安全沙箱。 */
final class PostgresqlDdlScriptPolicy {
    private static final int MAX_SCRIPT_BYTES = 1024 * 1024;
    private static final Set<String> HEADS = Set.of("CREATE TABLE", "CREATE COLLATION", "CREATE INDEX",
            "CREATE UNIQUE", "CREATE SEQUENCE", "ALTER TABLE", "COMMENT ON", "INSERT INTO", "UPDATE", "DELETE FROM");

    private PostgresqlDdlScriptPolicy() { }

    static void validate(String path) {
        if (path == null || path.length() > 500
                || !path.matches("db/postgresql/[A-Za-z0-9][A-Za-z0-9/_-]*\\.sql")
                || path.contains("//") || new File(path).exists()) {
            throw new IllegalStateException("PG_DDL_CLASSPATH_REQUIRED");
        }
        try (var input = Resources.getResourceAsStream(path)) {
            byte[] bytes = input.readNBytes(MAX_SCRIPT_BYTES + 1);
            if (bytes.length > MAX_SCRIPT_BYTES) {
                throw new IllegalStateException("PG_DDL_SCRIPT_LIMIT_EXCEEDED");
            }
            validateSql(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
        }
    }

    /** 逐语句识别头部；引号与嵌套注释内的分号、关键字不当作事务控制。 */
    static void validateSql(String sql) {
        if (sql.toUpperCase(Locale.ROOT).contains("@DELIMITER")) {
            throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
        }
        StringBuilder statement = new StringBuilder();
        for (int index = 0; index < sql.length();) {
            char current = sql.charAt(index);
            if (current == '\'' || current == '"') {
                char quote = current;
                statement.append(" quoted ");
                boolean closed = false;
                for (index++; index < sql.length(); index++) {
                    if (sql.charAt(index) == '\\') {
                        throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
                    }
                    if (sql.charAt(index) == quote) {
                        if (index + 1 < sql.length() && sql.charAt(index + 1) == quote) {
                            index++;
                        } else {
                            index++;
                            closed = true;
                            break;
                        }
                    }
                }
                if (!closed) {
                    throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
                }
            } else if (index + 1 < sql.length() && sql.startsWith("--", index)) {
                int end = sql.indexOf('\n', index + 2);
                index = end < 0 ? sql.length() : end + 1;
                statement.append(' ');
            } else if (index + 1 < sql.length() && sql.startsWith("/*", index)) {
                int depth = 1;
                for (index += 2; index < sql.length() && depth > 0;) {
                    if (sql.startsWith("/*", index)) { depth++; index += 2; }
                    else if (sql.startsWith("*/", index)) { depth--; index += 2; }
                    else { index++; }
                }
                if (depth != 0) { throw new IllegalStateException("PG_DDL_SOURCE_REJECTED"); }
                statement.append(' ');
            } else if (current == '$' || current == '\\') {
                // 本批 migration 不含过程块或 E-string；不猜测另一套 delimiter/转义语义。
                throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
            } else if (current == ';') {
                validateHead(statement.toString());
                statement.setLength(0);
                index++;
            } else {
                statement.append(current);
                index++;
            }
        }
        if (!statement.toString().isBlank()) {
            // 官方 ScriptRunner 在缺失分隔符时曾先 commit；预检必须先拒绝。
            throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
        }
    }

    private static void validateHead(String statement) {
        String normalized = statement.strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.isEmpty()) { return; }
        boolean allowed = HEADS.stream().anyMatch(head -> normalized.startsWith(head + " "));
        if (!allowed || normalized.contains(" CONCURRENTLY ")
                || (normalized.startsWith("CREATE UNIQUE ") && !normalized.startsWith("CREATE UNIQUE INDEX "))) {
            throw new IllegalStateException("PG_DDL_SOURCE_REJECTED");
        }
    }
}
