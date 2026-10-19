package com.hrms.auth.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogTextTest {

    @Test
    void lineBreaksBecomeQuestionMarks() {
        assertEquals("abc??INFO [x] Login OK", LogText.clean("abc\r\nINFO [x] Login OK"));
    }

    @Test
    void unicodeLineSeparatorsAndInvisibleFormatCharactersAreReplacedToo() {
        assertEquals("a?b?c?d", LogText.clean("a b c‮d"));
        assertEquals("a?b", LogText.clean("a\u0085b"));
    }

    @Test
    void overlongTextIsCutToOneHundredCharacters() {
        assertEquals(100, LogText.clean("x".repeat(500)).length());
    }

    @Test
    void ordinaryTextAndArabicPassThroughUnchanged() {
        assertEquals("jaya.choudhury@example.com", LogText.clean("jaya.choudhury@example.com"));
        assertEquals("محمد", LogText.clean("محمد"));
    }

    @Test
    void nullStaysNull() {
        assertNull(LogText.clean(null));
    }
}
