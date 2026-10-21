package com.hrms.employee.dto.response;

/** Dropdown entry for a branch; {@code locationId} lets a form offer only the branches of a chosen location. */
public record BranchLookupResponse(Long id, String nameEn, String nameAr, Long locationId) {
}
