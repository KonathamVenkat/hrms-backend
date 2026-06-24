package com.hrms.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Marital status enum matching Oracle CHECK constraint:
 * CHK_EMP_MARITAL CHECK (MARITAL_STATUS IN ('SINGLE','MARRIED','DIVORCED','WIDOWED','SEPARATED'))
 */
public enum MaritalStatus {

    SINGLE("Single"),
    MARRIED("Married"),
    DIVORCED("Divorced"),
    WIDOWED("Widowed"),
    SEPARATED("Separated");

    private final String displayName;

    MaritalStatus(String displayName) {
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
    public static MaritalStatus fromValue(String value) {
        for (MaritalStatus ms : MaritalStatus.values()) {
            if (ms.name().equalsIgnoreCase(value)) {
                return ms;
            }
        }
        throw new IllegalArgumentException(
            "Invalid marital status: '" + value
                + "'. Accepted values: SINGLE, MARRIED, DIVORCED, WIDOWED, SEPARATED"
        );
    }
}
