package com.hrms.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Employment status enum matching Oracle CHECK constraint:
 * CHK_EMP_STATUS CHECK (EMPLOYMENT_STATUS IN (
 *   'ACTIVE','PROBATION','NOTICE_PERIOD','TERMINATED',
 *   'RESIGNED','RETIRED','ON_HOLD','ABSCONDED'
 * ))
 * Default in Oracle schema: 'PROBATION'
 */
public enum EmploymentStatus {

    ACTIVE("Active"),
    PROBATION("Probation"),
    NOTICE_PERIOD("Notice Period"),
    TERMINATED("Terminated"),
    RESIGNED("Resigned"),
    RETIRED("Retired"),
    ON_HOLD("On Hold"),
    ABSCONDED("Absconded");

    private final String displayName;

    EmploymentStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getValue() {
        return this.name();
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if the employee is currently working (not exited).
     */
    public boolean isCurrentlyEmployed() {
        return this == ACTIVE || this == PROBATION || this == NOTICE_PERIOD || this == ON_HOLD;
    }

    @JsonCreator
    public static EmploymentStatus fromValue(String value) {
        for (EmploymentStatus es : EmploymentStatus.values()) {
            if (es.name().equalsIgnoreCase(value)) {
                return es;
            }
        }
        throw new IllegalArgumentException(
            "Invalid employment status: '" + value
                + "'. Accepted values: ACTIVE, PROBATION, NOTICE_PERIOD, TERMINATED, "
                + "RESIGNED, RETIRED, ON_HOLD, ABSCONDED"
        );
    }
}
