package com.hrms.payroll.dto.response;

/** An active loan type (a salary component flagged IS_LOAN) for the loan form's dropdown. */
public record LoanTypeResponse(Long id, String code, String name, String nameAr) {}
