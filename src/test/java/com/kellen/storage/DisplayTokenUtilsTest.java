package com.kellen.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 展示 token：库里只存 SHA-256 哈希，展示时用明文比对。 */
class DisplayTokenUtilsTest {

    @Test
    void hashIsLowercaseSha256Hex() {
        assertThat(DisplayTokenUtils.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void matchesOnlyTheTokenWhoseHashWasStored() {
        String stored = DisplayTokenUtils.hash("synthetic-token");
        assertThat(DisplayTokenUtils.matches("synthetic-token", stored)).isTrue();
        assertThat(DisplayTokenUtils.matches("other-token", stored)).isFalse();
    }

    @Test
    void blankTokenOrStoredHashIsRejected() {
        assertThatThrownBy(() -> DisplayTokenUtils.hash(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DisplayTokenUtils.matches(null, DisplayTokenUtils.hash("synthetic-token")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DisplayTokenUtils.matches("synthetic-token", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
