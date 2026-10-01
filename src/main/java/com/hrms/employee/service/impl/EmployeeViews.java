package com.hrms.employee.service.impl;

import com.hrms.employee.dto.response.EmployeeDetailResponse;
import com.hrms.employee.dto.response.EmployeeSummaryResponse;
import com.hrms.employee.repository.projection.EmployeeDetailProjection;
import com.hrms.employee.repository.projection.EmployeeListProjection;

/** Turns the read-only query projections into the response objects the API returns. */
public final class EmployeeViews {

    private EmployeeViews() {}

    public static EmployeeSummaryResponse summary(EmployeeListProjection p) {
        return EmployeeSummaryResponse.builder()
            .employeeId(p.getEmployeeId())
            .employeeCode(p.getEmployeeCode())
            .fullNameEn(fullName(p.getFirstName(), p.getMiddleName(), p.getLastName()))
            .firstNameAr(p.getFirstNameAr())
            .lastNameAr(p.getLastNameAr())
            .gender(p.getGender())
            .workEmail(p.getWorkEmail())
            .workPhone(p.getWorkPhone())
            .profilePhotoUrl(p.getProfilePhotoUrl())
            .employmentStatus(p.getEmploymentStatus())
            .employmentType(p.getEmploymentType())
            .nationality(p.getNationality())
            .hireDate(p.getHireDate())
            .isActive(p.getIsActive() != null && p.getIsActive() == 1)
            .departmentId(p.getDepartmentId())
            .departmentName(p.getDepartmentName())
            .departmentCode(p.getDepartmentCode())
            .designationId(p.getDesignationId())
            .designationTitle(p.getDesignationTitle())
            .gradeLevel(p.getGradeLevel())
            .build();
    }

    public static EmployeeDetailResponse detail(EmployeeDetailProjection p) {
        return EmployeeDetailResponse.builder()
            .employeeId(p.getEmployeeId())
            .employeeCode(p.getEmployeeCode())
            .firstName(p.getFirstName())
            .firstNameAr(p.getFirstNameAr())
            .middleName(p.getMiddleName())
            .middleNameAr(p.getMiddleNameAr())
            .lastName(p.getLastName())
            .lastNameAr(p.getLastNameAr())
            .fullNameEn(fullName(p.getFirstName(), p.getMiddleName(), p.getLastName()))
            .fullNameAr(fullName(p.getFirstNameAr(), p.getMiddleNameAr(), p.getLastNameAr()))
            .dateOfBirth(p.getDateOfBirth())
            .gender(p.getGender())
            .bloodGroup(p.getBloodGroup())
            .maritalStatus(p.getMaritalStatus())
            .nationality(p.getNationality())
            .religion(p.getReligion())
            .profilePhotoUrl(p.getProfilePhotoUrl())
            .personalEmail(p.getPersonalEmail())
            .workEmail(p.getWorkEmail())
            .personalPhone(p.getPersonalPhone())
            .workPhone(p.getWorkPhone())
            .hireDate(p.getHireDate())
            .probationEndDate(p.getProbationEndDate())
            .confirmationDate(p.getConfirmationDate())
            .employmentStatus(p.getEmploymentStatus())
            .employmentType(p.getEmploymentType())
            .isActive(p.getIsActive() != null && p.getIsActive() == 1)
            .departmentId(p.getDepartmentId())
            .departmentName(p.getDepartmentName())
            .departmentCode(p.getDepartmentCode())
            .departmentNameAr(p.getDepartmentNameAr())
            .designationId(p.getDesignationId())
            .designationTitle(p.getDesignationTitle())
            .designationTitleAr(p.getDesignationTitleAr())
            .designationCode(p.getDesignationCode())
            .gradeLevel(p.getGradeLevel())
            .createdBy(p.getCreatedBy())
            .createdAt(p.getCreatedAt())
            .updatedBy(p.getUpdatedBy())
            .updatedAt(p.getUpdatedAt())
            .build();
    }

    private static String fullName(String first, String middle, String last) {
        StringBuilder name = new StringBuilder();
        if (first != null) name.append(first.trim());
        if (middle != null && !middle.isBlank()) name.append(" ").append(middle.trim());
        if (last != null) name.append(" ").append(last.trim());
        return name.toString().trim();
    }
}
