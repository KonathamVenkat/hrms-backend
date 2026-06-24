package com.hrms.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gender enum matching Oracle CHECK constraint:
 * CHK_EMP_GENDER CHECK (GENDER IN ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY'))
 */
public enum Gender {

    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other"),
    PREFER_NOT_TO_SAY("Prefer Not To Say");

    private final String displayName;

    Gender(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return this.name();
    }

    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static Gender fromValue(String value) {
        for (Gender gender : Gender.values()) {
            if (gender.name().equalsIgnoreCase(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException(
            "Invalid gender value: '" + value + "'. Accepted values: MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY"
        );
    }
}
