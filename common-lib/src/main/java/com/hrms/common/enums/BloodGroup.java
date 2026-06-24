package com.hrms.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Blood group enum matching Oracle CHECK constraint:
 * CHK_EMP_BLOOD CHECK (BLOOD_GROUP IN ('A+','A-','B+','B-','AB+','AB-','O+','O-'))
 */
public enum BloodGroup {

    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    /**
     * Returns the Oracle-compatible string representation (e.g., "A+", "O-")
     * used when persisting to the database via @Enumerated(STRING) + converter.
     */
    public String getLabel() {
        return label;
    }

    @JsonValue
    public String getValue() {
        return this.label;
    }

    @JsonCreator
    public static BloodGroup fromValue(String value) {
        if (value == null) return null;
        for (BloodGroup bg : BloodGroup.values()) {
            if (bg.label.equalsIgnoreCase(value) || bg.name().equalsIgnoreCase(value)) {
                return bg;
            }
        }
        throw new IllegalArgumentException(
            "Invalid blood group value: '" + value
                + "'. Accepted values: A+, A-, B+, B-, AB+, AB-, O+, O-"
        );
    }
}
