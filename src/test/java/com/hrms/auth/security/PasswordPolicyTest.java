package com.hrms.auth.security;

import com.hrms.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The length limit is BCrypt's 72 bytes, not 72 characters. */
class PasswordPolicyTest {

    @Test
    void a72ByteEnglishPasswordIsAccepted_and73IsRefused() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Aa1" + "x".repeat(69)));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> PasswordPolicy.validate("Aa1" + "x".repeat(70)));
        assertEquals("PASSWORD_POLICY", ex.getRuleCode());
    }

    @Test
    void anArabicPasswordUnder72CharactersButOver72BytesIsRefusedWithThePolicyMessage() {
        // 43 characters, 83 bytes: it used to pass the policy and then crash the BCrypt encoder with a server error.
        String arabic = "كلمة".repeat(10) + "Aa1";
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> PasswordPolicy.validate(arabic));
        assertEquals("PASSWORD_POLICY", ex.getRuleCode());
    }

    @Test
    void aShortArabicPasswordWithTheRequiredMixIsAccepted() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("كلمةسر" + "Aa1"));
    }
}
