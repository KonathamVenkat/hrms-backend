package com.hrms.common.enums;

public enum UserRole {
    HR_ADMIN,
    HR_MANAGER,
    EMPLOYEE;

    public String asAuthority() {
        return "ROLE_" + this.name();
    }
}
