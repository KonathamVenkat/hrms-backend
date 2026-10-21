package com.hrms.recruitment.enums;

/** What a pipeline stage means to the code; stage names and codes are free for SYSTEM_ADMIN to change. */
public enum StageType {
    OPEN, HIRED, REJECTED, WITHDRAWN;

    public boolean isTerminal() {
        return this != OPEN;
    }
}
