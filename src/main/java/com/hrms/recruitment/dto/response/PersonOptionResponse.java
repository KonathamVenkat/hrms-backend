package com.hrms.recruitment.dto.response;

/** One active employee of a department, for the reporting-manager and replaced-employee pickers. */
public record PersonOptionResponse(Long id, String code, String name, String nameAr) { }
