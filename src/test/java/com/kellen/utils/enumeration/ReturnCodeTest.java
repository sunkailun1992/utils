package com.kellen.utils.enumeration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码改英文命名后，编码与文案必须与改名前导出的快照逐项一致；常量名为英文大写下划线。
 */
class ReturnCodeTest {

    @Test
    void codesAndMessagesMatchSnapshot() throws IOException {
        List<String> expected = snapshot();
        List<String> actual = Arrays.stream(ReturnCode.values())
                .map(code -> code.getCode() + "\t" + code.getMessage())
                .toList();

        assertThat(actual).containsExactlyElementsOf(expected);
    }

    @Test
    void constantNamesAreUpperSnakeCase() {
        for (ReturnCode code : ReturnCode.values()) {
            assertThat(code.name()).matches("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");
        }
    }

    @Test
    void codesAreUnique() {
        Map<String, Long> counts = Arrays.stream(ReturnCode.values())
                .collect(Collectors.groupingBy(ReturnCode::getCode, Collectors.counting()));

        assertThat(counts.values()).allMatch(count -> count == 1);
    }

    @Test
    void referencedConstantsKeepTheirCodes() {
        Map<ReturnCode, String> pinned = Map.ofEntries(
                Map.entry(ReturnCode.SUCCESS, "0"),
                Map.entry(ReturnCode.CLIENT_ERROR, "A0001"),
                Map.entry(ReturnCode.USER_ACCOUNT_FROZEN, "A0202"),
                Map.entry(ReturnCode.USER_PASSWORD_ERROR, "A0210"),
                Map.entry(ReturnCode.USER_AUTHENTICATION_FAILED, "A0220"),
                Map.entry(ReturnCode.THIRD_PARTY_LOGIN_NOT_AUTHORIZED, "A0223"),
                Map.entry(ReturnCode.USER_LOGIN_EXPIRED, "A0230"),
                Map.entry(ReturnCode.INVALID_TOKEN, "A0116"),
                Map.entry(ReturnCode.PERMISSION_DENIED, "A0312"),
                Map.entry(ReturnCode.USER_SIGNATURE_ERROR, "A0340"),
                Map.entry(ReturnCode.RSA_SIGNATURE_ERROR, "A0341"),
                Map.entry(ReturnCode.REQUEST_PARAMETER_ERROR, "A0400"),
                Map.entry(ReturnCode.REQUIRED_PARAMETER_MISSING, "A0410"),
                Map.entry(ReturnCode.INVALID_TIMESTAMP_PARAMETER, "A0414"),
                Map.entry(ReturnCode.DUPLICATE_REQUEST, "A0506"),
                Map.entry(ReturnCode.SYSTEM_EXECUTION_ERROR, "B0001"),
                Map.entry(ReturnCode.MIDDLEWARE_SERVICE_ERROR, "C0100"),
                Map.entry(ReturnCode.RPC_SERVICE_ERROR, "C0110"),
                Map.entry(ReturnCode.DATABASE_SERVICE_ERROR, "C0300"),
                Map.entry(ReturnCode.NOTIFICATION_SERVICE_ERROR, "C0500"));

        pinned.forEach((code, value) -> assertThat(code.getCode()).as(code.name()).isEqualTo(value));
    }

    private static List<String> snapshot() throws IOException {
        try (InputStream input = Objects.requireNonNull(
                ReturnCodeTest.class.getResourceAsStream("/return-codes.tsv"), "return-codes.tsv")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
    }
}
