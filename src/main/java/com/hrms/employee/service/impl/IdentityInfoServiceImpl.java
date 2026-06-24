package com.hrms.employee.service.impl;

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

    // ── Get identity info ─────────────────────────────────────
    @Override
    public IdentityInfoResponse getIdentityInfo(Long employeeId) {
        validateEmployee(employeeId);

        return identityRepository.findByEmployeeId(employeeId)
            .map(this::toResponse)
            .orElseGet(() -> emptyResponse(employeeId));
    }

    // ── Save (upsert) ─────────────────────────────────────────
    @Override
    @Transactional
    public IdentityInfoResponse saveIdentityInfo(Long employeeId,
                                                  IdentityInfoRequest request) {
        log.info("Saving identity info for employee {}", employeeId);
        validateEmployee(employeeId);

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
            entity.setNationalId(clean(request.getNationalId()));
            entity.setPassportNumber(clean(request.getPassportNumber()));
            entity.setTaxId(clean(request.getTaxId()));
            entity.setSocialSecurityNumber(clean(request.getSocialSecurityNumber()));
            entity.setDrivingLicenseNumber(clean(request.getDrivingLicenseNumber()));
            entity.setVisaNumber(clean(request.getVisaNumber()));
            entity.setVisaType(clean(request.getVisaType()));
            entity.setVisaIssueDate(request.getVisaIssueDate());
            entity.setVisaExpiryDate(request.getVisaExpiryDate());
            entity.setWorkPermitNumber(clean(request.getWorkPermitNumber()));
            entity.setWorkPermitExpiry(request.getWorkPermitExpiry());
            entity.setBiometricId(clean(request.getBiometricId()));
            entity.setUpdatedAt(LocalDateTime.now());
            log.info("Updated identity info for employee {}", employeeId);
        } else {
            // ── Create new record ─────────────────────────────
            entity = EmployeeIdentityInfo.builder()
                .employeeId(employeeId)
                .nationalId(clean(request.getNationalId()))
                .passportNumber(clean(request.getPassportNumber()))
                .taxId(clean(request.getTaxId()))
                .socialSecurityNumber(clean(request.getSocialSecurityNumber()))
                .drivingLicenseNumber(clean(request.getDrivingLicenseNumber()))
                .visaNumber(clean(request.getVisaNumber()))
                .visaType(clean(request.getVisaType()))
                .visaIssueDate(request.getVisaIssueDate())
                .visaExpiryDate(request.getVisaExpiryDate())
                .workPermitNumber(clean(request.getWorkPermitNumber()))
                .workPermitExpiry(request.getWorkPermitExpiry())
                .biometricId(clean(request.getBiometricId()))
                .createdAt(LocalDateTime.now())
                .build();
            log.info("Created identity info for employee {}", employeeId);
        }

        return toResponse(identityRepository.save(entity));
    }

    // ── Private helpers ───────────────────────────────────────

    private void validateEmployee(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
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

    private IdentityInfoResponse toResponse(EmployeeIdentityInfo info) {
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
            .nationalId(info.getNationalId())
            .passportNumber(info.getPassportNumber())
            .taxId(info.getTaxId())
            .socialSecurityNumber(info.getSocialSecurityNumber())
            .drivingLicenseNumber(info.getDrivingLicenseNumber())
            .visaNumber(info.getVisaNumber())
            .visaType(info.getVisaType())
            .visaIssueDate(info.getVisaIssueDate())
            .visaExpiryDate(info.getVisaExpiryDate())
            .visaExpiringSoon(visaExpiringSoon)
            .workPermitNumber(info.getWorkPermitNumber())
            .workPermitExpiry(info.getWorkPermitExpiry())
            .workPermitExpiringSoon(permitExpiringSoon)
            .biometricId(info.getBiometricId())
            .createdAt(info.getCreatedAt() != null
                ? info.getCreatedAt().toString() : null)
            .updatedAt(info.getUpdatedAt() != null
                ? info.getUpdatedAt().toString() : null)
            .build();
    }

    /** Empty response when no identity record exists yet */
    private IdentityInfoResponse emptyResponse(Long employeeId) {
        return IdentityInfoResponse.builder()
            .employeeId(employeeId)
            .build();
    }

    private String clean(String val) {
        return (val != null && !val.isBlank()) ? val.trim() : null;
    }

    private boolean hasValue(String val) {
        return val != null && !val.isBlank();
    }
}
