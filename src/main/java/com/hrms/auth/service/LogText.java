package com.hrms.auth.service;

/** Makes text typed by a caller safe to put in a log line or an audit field. */
public final class LogText {

    private static final int MAX_LENGTH = 100;

    private LogText() {
    }

    /**
     * Control characters (CR and LF included), invisible format characters and Unicode line separators
     * become '?', and the result is cut to {@value #MAX_LENGTH} characters, so one request cannot forge
     * extra log lines.
     */
    public static String clean(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\p{Cc}\\p{Cf}\\u2028\\u2029]", "?");
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) : s;
    }
}
