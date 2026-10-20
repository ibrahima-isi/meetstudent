package com.bowe.meetstudent.unit.validation;

import com.bowe.meetstudent.dto.validation.SafeText;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SafeTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"a\u0007b", "a\u0000b", "a\u001bb", "a\u0085b", "a‮b", "a‪b", "a⁦b", "a⁩b",
            "a​b", "a‏b", "a﻿b", "a b", "a b", "a\ud800b", "a\udc00"})
    void refusesControlFormatSeparatorAndLoneSurrogateCharacters(String text) {
        assertThat(SafeText.hasForbiddenCharacters(text)).isTrue();
    }

    @Test
    void acceptsAccentsEmojiAndTabCrLf() {
        assertThat(SafeText.hasForbiddenCharacters("Diplôme à vérifier 😀 çà\tb\r\nc")).isFalse();
        assertThat(SafeText.hasForbiddenCharacters("")).isFalse();
    }
}
