package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.util.Strings;
import com.hrms.employee.service.EmployeeChecks;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.IdentityInfoRequest;
import com.hrms.employee.dto.response.IdentityInfoResponse;
import com.hrms.employee.entity.EmployeeIdentityInfo;
import com.hrms.employee.repository.EmployeeIdentityInfoRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.IdentityInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityInfoServiceImpl implements IdentityInfoService {

    private final EmployeeIdentityInfoRepository identityRepository;
    private final EmployeeRepository             employeeRepository;
    private final EmployeeAccessGuard            accessGuard;
    private final AuditTrail                     audit;

    // ── Get identity info ─────────────────────────────────────
    @Override
    public IdentityInfoResponse getIdentityInfo(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        // HR_ADMIN and the employee themself see real values; everyone else (HR_MANAGER) gets last-4 only.
        boolean masked = !accessGuard.canViewUnmasked(employeeId);

        return identityRepository.findByEmployeeId(employeeId)
            .map(info -> toResponse(info, masked))
            .orElseGet(() -> emptyResponse(employeeId));
    }

    // ── Save (upsert) ─────────────────────────────────────────
    @Override
    @Transactional
    public IdentityInfoResponse saveIdentityInfo(Long employeeId,
                                                  IdentityInfoRequest request) {
        log.info("Saving identity info for employee {}", employeeId);
        EmployeeChecks.requireExists(employeeRepository, employeeId);

        // ── Uniqueness validations ────────────────────────────
        validateUniqueFields(employeeId, request);

        // ── Visa date validation ───────────────────────────────
        if (request.getVisaIssueDate() != null && request.getVisaExpiryDate() != null) {
            if (request.getVisaExpiryDate().isBefore(request.getVisaIssueDate())) {
                throw new BusinessRuleException(
                    "INVALID_VISA_DATES",
                    "Visa expiry date must be after issue date.");
            }
        }

        requireOrderedDates(request.getNationalIdIssueDate(), null, "INVALID_NATIONAL_ID_DATES",
            "The national ID issue date cannot be in the future.");
        requireOrderedDates(request.getResidenceDate(), request.getResidenceValidUntil(), "INVALID_RESIDENCE_DATES",
            "The residence valid-until date cannot be before the residence date.");

        Optional<EmployeeIdentityInfo> existing =
            identityRepository.findByEmployeeId(employeeId);

        EmployeeIdentityInfo entity;
        String changed = "created";

        if (existing.isPresent()) {
            // ── Update existing record ────────────────────────
            entity = existing.get();
            // An update must say which version it was made from, so it cannot silently overwrite what HR or the
            // employee saved since (the screen always sends it).
            if (request.getVersion() == null) {
                throw new BusinessRuleException("VERSION_REQUIRED",
                    "Reload the identity details and try again.");
            }
            Versions.requireCurrent(request.getVersion(), entity.getVersion());
            changed = changedFields(entity, request);
            entity.setNationalId(Strings.trimToNull(request.getNationalId()));
            applyDocumentsAndResidence(entity, request);
            entity.setPassportNumber(Strings.trimToNull(request.getPassportNumber()));
            entity.setTaxId(Strings.trimToNull(request.getTaxId()));
            entity.setSocialSecurityNumber(Strings.trimToNull(request.getSocialSecurityNumber()));
            entity.setDrivingLicenseNumber(Strings.trimToNull(request.getDrivingLicenseNumber()));
            entity.setVisaNumber(Strings.trimToNull(request.getVisaNumber()));
            entity.setVisaType(Strings.trimToNull(request.getVisaType()));
            entity.setVisaIssueDate(request.getVisaIssueDate());
            entity.setVisaExpiryDate(request.getVisaExpiryDate());
            entity.setWorkPermitNumber(Strings.trimToNull(request.getWorkPermitNumber()));
            entity.setWorkPermitExpiry(request.getWorkPermitExpiry());
            entity.setBiometricId(Strings.trimToNull(request.getBiometricId()));
            entity.setUpdatedAt(LocalDateTime.now());
            log.info("Updated identity info for employee {}", employeeId);
        } else {
            // ── Create new record ─────────────────────────────
            entity = EmployeeIdentityInfo.builder()
                .employeeId(employeeId)
                .nationalId(Strings.trimToNull(request.getNationalId()))
                .nationalIdType(Strings.trimToNull(request.getNationalIdType()))
                .nationalIdIssueDate(request.getNationalIdIssueDate())
                .nationalIdIssuePlace(Strings.trimToNull(request.getNationalIdIssuePlace()))
                .residenceDate(request.getResidenceDate())
                .residencePlace(Strings.trimToNull(request.getResidencePlace()))
                .residenceValidUntil(request.getResidenceValidUntil())
                .passportNumber(Strings.trimToNull(request.getPassportNumber()))
                .taxId(Strings.trimToNull(request.getTaxId()))
                .socialSecurityNumber(Strings.trimToNull(request.getSocialSecurityNumber()))
                .drivingLicenseNumber(Strings.trimToNull(request.getDrivingLicenseNumber()))
                .visaNumber(Strings.trimToNull(request.getVisaNumber()))
                .visaType(Strings.trimToNull(request.getVisaType()))
                .visaIssueDate(request.getVisaIssueDate())
                .visaExpiryDate(request.getVisaExpiryDate())
                .workPermitNumber(Strings.trimToNull(request.getWorkPermitNumber()))
                .workPermitExpiry(request.getWorkPermitExpiry())
                .biometricId(Strings.trimToNull(request.getBiometricId()))
                .build();
            log.info("Created identity info for employee {}", employeeId);
        }

        // Flush so the response carries the incremented version: the screen sends it back with its next save.
        EmployeeIdentityInfo saved = identityRepository.saveAndFlush(entity);
        // Field names only: the values are sensitive and never go to the audit trail.
        audit.record("EMPLOYEE_IDENTITY_SAVED", "EMPLOYEE_IDENTITY", employeeId, changed);
        return toResponse(saved, false); // HR_ADMIN or the employee themself
    }

    // ── Private helpers ───────────────────────────────────────

    /** "updated: nationalId, visaExpiryDate" — the names of the fields whose value differs from what is stored. */
    private static String changedFields(EmployeeIdentityInfo e, IdentityInfoRequest r) {
        java.util.Map<String, Boolean> differs = new java.util.LinkedHashMap<>();
        differs.put("nationalId", !java.util.Objects.equals(e.getNationalId(), Strings.trimToNull(r.getNationalId())));
        differs.put("nationalIdType", !java.util.Objects.equals(e.getNationalIdType(), Strings.trimToNull(r.getNationalIdType())));
        differs.put("nationalIdIssueDate", !java.util.Objects.equals(e.getNationalIdIssueDate(), r.getNationalIdIssueDate()));
        differs.put("nationalIdIssuePlace", !java.util.Objects.equals(e.getNationalIdIssuePlace(), Strings.trimToNull(r.getNationalIdIssuePlace())));
        differs.put("residenceDate", !java.util.Objects.equals(e.getResidenceDate(), r.getResidenceDate()));
        differs.put("residencePlace", !java.util.Objects.equals(e.getResidencePlace(), Strings.trimToNull(r.getResidencePlace())));
        differs.put("residenceValidUntil", !java.util.Objects.equals(e.getResidenceValidUntil(), r.getResidenceValidUntil()));
        differs.put("passportNumber", !java.util.Objects.equals(e.getPassportNumber(), Strings.trimToNull(r.getPassportNumber())));
        differs.put("taxId", !java.util.Objects.equals(e.getTaxId(), Strings.trimToNull(r.getTaxId())));
        differs.put("socialSecurityNumber", !java.util.Objects.equals(e.getSocialSecurityNumber(), Strings.trimToNull(r.getSocialSecurityNumber())));
        differs.put("drivingLicenseNumber", !java.util.Objects.equals(e.getDrivingLicenseNumber(), Strings.trimToNull(r.getDrivingLicenseNumber())));
        differs.put("visaNumber", !java.util.Objects.equals(e.getVisaNumber(), Strings.trimToNull(r.getVisaNumber())));
        differs.put("visaType", !java.util.Objects.equals(e.getVisaType(), Strings.trimToNull(r.getVisaType())));
        differs.put("visaIssueDate", !java.util.Objects.equals(e.getVisaIssueDate(), r.getVisaIssueDate()));
        differs.put("visaExpiryDate", !java.util.Objects.equals(e.getVisaExpiryDate(), r.getVisaExpiryDate()));
        differs.put("workPermitNumber", !java.util.Objects.equals(e.getWorkPermitNumber(), Strings.trimToNull(r.getWorkPermitNumber())));
        differs.put("workPermitExpiry", !java.util.Objects.equals(e.getWorkPermitExpiry(), r.getWorkPermitExpiry()));
        differs.put("biometricId", !java.util.Objects.equals(e.getBiometricId(), Strings.trimToNull(r.getBiometricId())));
        String names = differs.entrySet().stream().filter(java.util.Map.Entry::getValue)
            .map(java.util.Map.Entry::getKey).collect(java.util.stream.Collectors.joining(", "));
        return "updated: " + (names.isEmpty() ? "no change" : names);
    }

    private static void applyDocumentsAndResidence(EmployeeIdentityInfo entity, IdentityInfoRequest request) {
        entity.setNationalIdType(Strings.trimToNull(request.getNationalIdType()));
        entity.setNationalIdIssueDate(request.getNationalIdIssueDate());
        entity.setNationalIdIssuePlace(Strings.trimToNull(request.getNationalIdIssuePlace()));
        entity.setResidenceDate(request.getResidenceDate());
        entity.setResidencePlace(Strings.trimToNull(request.getResidencePlace()));
        entity.setResidenceValidUntil(request.getResidenceValidUntil());
    }

    /** With an end date: it cannot precede the start. Without one: the start cannot be in the future. */
    private static void requireOrderedDates(LocalDate start, LocalDate end, String rule, String message) {
        if (start == null) return;
        boolean wrong = end != null ? end.isBefore(start) : start.isAfter(LocalDate.now());
        if (wrong) throw new BusinessRuleException(rule, message);
    }

    private void validateUniqueFields(Long employeeId, IdentityInfoRequest req) {
        if (hasValue(req.getNationalId()) &&
            identityRepository.existsByNationalIdAndEmployeeIdNot(
                req.getNationalId(), employeeId)) {
            throw new BusinessRuleException(
                "DUPLICATE_NATIONAL_ID",
                "National ID already exists for another employee.");
        }
        if (hasValue(req.getPassportNumber()) &&
            identityRepository.existsByPassportNumberAndEmployeeIdNot(
                req.getPassportNumber(), employeeId)) {
            throw new BusinessRuleException(
                "DUPLICATE_PASSPORT",
                "Passport number already exists for another employee.");
        }
        if (hasValue(req.getVisaNumber()) &&
            identityRepository.existsByVisaNumberAndEmployeeIdNot(
                req.getVisaNumber(), employeeId)) {
            throw new BusinessRuleException(
                "DUPLICATE_VISA",
                "Visa number already exists for another employee.");
        }
        if (hasValue(req.getWorkPermitNumber()) &&
            identityRepository.existsByWorkPermitNumberAndEmployeeIdNot(
                req.getWorkPermitNumber(), employeeId)) {
            throw new BusinessRuleException(
                "DUPLICATE_WORK_PERMIT",
                "Work permit number already exists for another employee.");
        }
    }

    private IdentityInfoResponse toResponse(EmployeeIdentityInfo info, boolean masked) {
        LocalDate today = LocalDate.now();
        LocalDate in60Days = today.plusDays(60);

        boolean visaExpiringSoon = info.getVisaExpiryDate() != null &&
            !info.getVisaExpiryDate().isBefore(today) &&
            !info.getVisaExpiryDate().isAfter(in60Days);

        boolean permitExpiringSoon = info.getWorkPermitExpiry() != null &&
            !info.getWorkPermitExpiry().isBefore(today) &&
            !info.getWorkPermitExpiry().isAfter(in60Days);

        return IdentityInfoResponse.builder()
            .employeeIdentityId(info.getEmployeeIdentityId())
            .employeeId(info.getEmployeeId())
            .nationalId(show(info.getNationalId(), masked))
            .nationalIdType(info.getNationalIdType())
            .nationalIdIssueDate(info.getNationalIdIssueDate())
            .nationalIdIssuePlace(info.getNationalIdIssuePlace())
            .residenceDate(info.getResidenceDate())
            .residencePlace(info.getResidencePlace())
            .residenceValidUntil(info.getResidenceValidUntil())
            .passportNumber(show(info.getPassportNumber(), masked))
            .taxId(show(info.getTaxId(), masked))
            .socialSecurityNumber(show(info.getSocialSecurityNumber(), masked))
            .drivingLicenseNumber(show(info.getDrivingLicenseNumber(), masked))
            .visaNumber(show(info.getVisaNumber(), masked))
            .visaType(info.getVisaType())
            .visaIssueDate(info.getVisaIssueDate())
            .visaExpiryDate(info.getVisaExpiryDate())
            .visaExpiringSoon(visaExpiringSoon)
            .workPermitNumber(show(info.getWorkPermitNumber(), masked))
            .workPermitExpiry(info.getWorkPermitExpiry())
            .workPermitExpiringSoon(permitExpiringSoon)
            .biometricId(show(info.getBiometricId(), masked))
            .masked(masked)
            .version(info.getVersion())
            .createdBy(info.getCreatedBy())
            .updatedBy(info.getUpdatedBy())
            .createdAt(info.getCreatedAt() != null
                ? info.getCreatedAt().toString() : null)
            .updatedAt(info.getUpdatedAt() != null
                ? info.getUpdatedAt().toString() : null)
            .build();
    }

    private String show(String value, boolean masked) {
        return masked ? mask(value) : value;
    }

    /** Keeps only the last 4 characters; values of 4 characters or fewer are fully masked. */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int visible = value.length() > 4 ? 4 : 0;
        return "*".repeat(value.length() - visible) + value.substring(value.length() - visible);
    }

    /** Empty response when no identity record exists yet */
    private IdentityInfoResponse emptyResponse(Long employeeId) {
        return IdentityInfoResponse.builder()
            .employeeId(employeeId)
            .build();
    }

    private boolean hasValue(String val) {
        return val != null && !val.isBlank();
    }
}
