package com.hrms.payroll.enums;

/**
 * Classifies a salary component.
 *
 * EARNING   → adds to gross pay  (Basic, HRA, Allowances, OT Pay)
 * DEDUCTION → subtracts from net (Absence, Advance)
 * STATUTORY → government-mandated (PASI contributions - Oman)
 */
public enum ComponentType {
    EARNING,
    DEDUCTION,
    STATUTORY
}
