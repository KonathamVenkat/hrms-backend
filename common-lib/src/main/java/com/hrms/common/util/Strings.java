package com.hrms.common.util;

public final class Strings {

    private Strings() {}

    /** The trimmed text, or {@code null} when it is null or blank. */
    public static String trimToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
