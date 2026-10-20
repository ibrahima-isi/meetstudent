package com.bowe.meetstudent.dto.validation;

/**
 * Filter for free text that admins read and that may later be put in mails. Refuses control characters (tab,
 * carriage return and line feed are kept), invisible format characters (bidi overrides and isolates, zero-width,
 * byte-order mark), Unicode line and paragraph separators and lone surrogates, which can hide or reorder text.
 */
public final class SafeText {

    private SafeText() {
    }

    public static boolean hasForbiddenCharacters(String text) {
        return text != null && text.codePoints().anyMatch(SafeText::isForbidden);
    }

    private static boolean isForbidden(int cp) {
        if (cp == '\t' || cp == '\r' || cp == '\n') return false;
        return switch (Character.getType(cp)) {
            case Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
                 Character.SURROGATE -> true;
            default -> false;
        };
    }
}
