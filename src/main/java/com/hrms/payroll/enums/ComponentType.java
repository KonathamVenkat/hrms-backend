package com.hrms.payroll.enums;

/**
 * Classifies a salary component.
 *
 * EARNING   → adds to gross pay  (Basic, HRA, Allowances, OT Pay)
 * DEDUCTION → subtracts from net (Absence, Advance)
 * STATUTORY → government-mandated (NSSF contributions)
 */
public enum ComponentType {
    EARNING,
    DEDUCTION,
    STATUTORY
}
