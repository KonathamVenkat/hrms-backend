package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.service.CityService;
import com.hrms.employee.service.EducationLevelService;
import com.hrms.employee.service.LanguageLevelService;
import com.hrms.employee.service.LanguageService;
import com.hrms.employee.service.NationalityService;
import com.hrms.employee.service.ProvinceService;
import com.hrms.employee.service.RelationService;
import com.hrms.employee.service.ReligionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only dropdowns of active reference values for the employee-information forms (HR_ADMIN, who edits them).
 * The employee-information responses carry the resolved names, so a reader never needs these.
 */
@RestController
@RequestMapping("/api/v1/lookups")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HR_ADMIN')")
public class ReferenceLookupController {

    private final NationalityService nationalities;
    private final ReligionService religions;
    private final ProvinceService provinces;
    private final CityService cities;
    private final RelationService relations;
    private final LanguageService languages;
    private final LanguageLevelService languageLevels;
    private final EducationLevelService educationLevels;

    /** Also read by HR_MANAGER: the employee create/edit form picks the nationality from it. */
    @GetMapping("/nationalities")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> nationalities() {
        return ok("Nationalities fetched successfully", nationalities.getActiveLookup());
    }

    /** The countries behind the nationalities (the address form's Country). */
    @GetMapping("/countries")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> countries() {
        return ok("Countries fetched successfully", nationalities.getActiveCountryLookup());
    }

    @GetMapping("/religions")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> religions() {
        return ok("Religions fetched successfully", religions.getActiveLookup());
    }

    /** Provinces (the address form's State / Province). */
    @GetMapping("/provinces")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> provinces() {
        return ok("Provinces fetched successfully", provinces.getActiveLookup());
    }

    /** Cities, each carrying its province id as parentId. */
    @GetMapping("/cities")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> cities() {
        return ok("Cities fetched successfully", cities.getActiveLookup());
    }

    @GetMapping("/relations")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> relations() {
        return ok("Relations fetched successfully", relations.getActiveLookup());
    }

    @GetMapping("/languages")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> languages() {
        return ok("Languages fetched successfully", languages.getActiveLookup());
    }

    @GetMapping("/language-levels")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> languageLevels() {
        return ok("Language levels fetched successfully", languageLevels.getActiveLookup());
    }

    @GetMapping("/education-levels")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> educationLevels() {
        return ok("Education levels fetched successfully", educationLevels.getActiveLookup());
    }

    private static ResponseEntity<ApiResponse<List<MasterLookupResponse>>> ok(String message, List<MasterLookupResponse> data) {
        return ResponseEntity.ok(ApiResponse.<List<MasterLookupResponse>>builder()
            .success(true).message(message).data(data).statusCode(200).build());
    }
}
