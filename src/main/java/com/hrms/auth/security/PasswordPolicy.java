package com.hrms.auth.security;

import com.hrms.common.exception.BusinessRuleException;

/**
 * The single definition of an acceptable new password, used by both change-password and
 * the HR_ADMIN reset. Deliberately NOT applied at login: existing accounts may hold older,
 * weaker passwords and must still be able to sign in and then be asked to change them.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt refuses more than 72 BYTES, which is fewer characters for Arabic and other non-ASCII text. */
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** Throws BusinessRuleException describing the first rule the password breaks. */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new BusinessRuleException("PASSWORD_POLICY",
                    "Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessRuleException("PASSWORD_POLICY",
                    "Password is too long: at most " + MAX_BYTES + " bytes (72 English letters, fewer in Arabic).");
        }
        boolean upper = false, lower = false, digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) upper = true;
            else if (Character.isLowerCase(c)) lower = true;
            else if (Character.isDigit(c)) digit = true;
        }
        if (!(upper && lower && digit)) {
            throw new BusinessRuleException("PASSWORD_POLICY",
                    "Password must contain an uppercase letter, a lowercase letter and a digit.");
        }
    }
}
