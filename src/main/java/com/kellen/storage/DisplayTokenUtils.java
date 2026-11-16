package com.kellen.storage;

import cn.hutool.crypto.digest.DigestUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 私有文件的展示 token：业务表只存哈希，展示时先用明文比对，通过后才签发短时访问地址。
 *
 * @author 孙凯伦
 */
public final class DisplayTokenUtils {

    private DisplayTokenUtils() {
    }

    /**
     * 计算展示 token 的 SHA-256 哈希（小写十六进制），首尾空白不计。
     *
     * @param token 展示 token 明文
     * @return 哈希
     */
    public static String hash(String token) {
        return DigestUtil.sha256Hex(requiredText(token, "token"));
    }

    /**
     * 明文与已存哈希是否一致，按常量时间比较。
     *
     * @param token      展示 token 明文
     * @param storedHash 业务表里存的哈希
     * @return 一致时为 true
     */
    public static boolean matches(String token, String storedHash) {
        byte[] actual = hash(token).getBytes(StandardCharsets.UTF_8);
        byte[] expected = requiredText(storedHash, "storedHash").getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value.trim();
    }
}
