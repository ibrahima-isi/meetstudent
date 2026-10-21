package com.bowe.meetstudent.dto.validation;

/**
 * Filter for free text that admins read and that may later be put in mails. Refuses control characters (tab,
 * carriage return and line feed are kept), invisible format characters (bidi overrides and isolates, zero-width,
 * byte-order mark), Unicode line and paragraph separators and lone surrogates, which can hide or reorder text, and the invisible filler letters (U+3164, U+2800, U+115F, U+1160,
 * U+FFA0, U+180E) that let a title or a name look empty.
 */
public final class SafeText {

    private SafeText() {
    }

    /** Blank-looking letters and symbols that render as nothing: Hangul fillers, Braille blank, mongolian vowel separator. */
    private static final String INVISIBLE_FILLERS = "\u3164\u2800\u115F\u1160\uFFA0\u180E";

    public static boolean hasForbiddenCharacters(String text) {
        return text != null && text.codePoints().anyMatch(SafeText::isForbidden);
    }

    private static boolean isForbidden(int cp) {
        if (cp == '\t' || cp == '\r' || cp == '\n') return false;
        if (INVISIBLE_FILLERS.indexOf(cp) >= 0) return true;
        return switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                 Character.SURROGATE -> true;
            default -> false;
        };
    }
}
