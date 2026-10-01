package com.hrms.employee.service.impl;

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

        Optional<EmployeeIdentityInfo> existing =
            identityRepository.findByEmployeeId(employeeId);

        EmployeeIdentityInfo entity;

        if (existing.isPresent()) {
            // ── Update existing record ────────────────────────
            entity = existing.get();
            entity.setNationalId(Strings.trimToNull(request.getNationalId()));
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
                .createdAt(LocalDateTime.now())
                .build();
            log.info("Created identity info for employee {}", employeeId);
        }

        return toResponse(identityRepository.save(entity), false); // HR_ADMIN only
    }

    // ── Private helpers ───────────────────────────────────────

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
