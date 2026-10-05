package com.hrms.payroll.enums;

/**
 * Determines how a salary component value is calculated.
 *
 * FIXED                → flat amount (e.g. Transport = 50 SSP)
 * PERCENTAGE_OF_BASIC  → % of basic salary (e.g. HRA = 25% of Basic)
 * PERCENTAGE_OF_GROSS  → % of gross salary (e.g. NSSF = a % of Gross)
 * FORMULA              → computed at payroll run time
 *                        (e.g. OT Pay = OT minutes / shift minutes * daily rate)
 *                        (e.g. Absence = absent days * daily rate)
 */
public enum CalculationType {
    FIXED,
    PERCENTAGE_OF_BASIC,
    PERCENTAGE_OF_GROSS,
    FORMULA
}
