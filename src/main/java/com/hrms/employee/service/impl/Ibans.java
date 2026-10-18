package com.hrms.employee.service.impl;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** IBAN format and check-digit test (ISO 13616 mod 97). Country-specific lengths are not checked. */
final class Ibans {

    private static final Pattern FORMAT = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$");

    private Ibans() {}

    /** @param normalised upper-case, no spaces */
    static boolean isValid(String normalised) {
        if (normalised == null || !FORMAT.matcher(normalised).matches()) return false;
        String rearranged = normalised.substring(4) + normalised.substring(0, 4);
        StringBuilder digits = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}
