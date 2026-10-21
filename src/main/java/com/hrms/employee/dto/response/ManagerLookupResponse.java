package com.hrms.employee.dto.response;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/** One person who can be picked as reporting or functional manager of a department. */
public record ManagerLookupResponse(
        Long employeeId,
        String employeeCode,
        String fullNameEn,
        String designationTitle,
        String gradeLevel) {

    /** Used by the JPQL constructor expression in EmployeeJobDetailsRepository. */
    public ManagerLookupResponse(Long employeeId, String employeeCode, String firstName, String middleName,
                                 String lastName, String designationTitle, String gradeLevel) {
        this(employeeId, employeeCode,
             Stream.of(firstName, middleName, lastName)
                   .filter(s -> s != null && !s.isBlank())
                   .collect(Collectors.joining(" ")),
             designationTitle, gradeLevel);
    }
}
