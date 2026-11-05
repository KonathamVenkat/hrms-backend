package com.hrms.employee.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.employee.service.EmployeeChecks;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.EmployeeAddressRequest;
import com.hrms.employee.dto.response.EmployeeAddressResponse;
import com.hrms.employee.entity.EmployeeAddress;
import com.hrms.employee.repository.EmployeeAddressRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.EmployeeAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeAddressServiceImpl implements EmployeeAddressService {

    private final EmployeeAddressRepository addressRepository;
    private final EmployeeRepository        employeeRepository;
    private final AuditTrail                audit;

    private static final String TARGET = "EMPLOYEE_ADDRESS";

    // ── Get all addresses ─────────────────────────────────────
    @Override
    public List<EmployeeAddressResponse> getAddresses(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        return addressRepository
            .findByEmployeeIdOrderByAddressTypeAsc(employeeId)
            .stream().map(this::toResponse)
            .collect(Collectors.toList());
    }

    // ── Get by ID ─────────────────────────────────────────────
    @Override
    public EmployeeAddressResponse getAddressById(Long employeeId, Long addressId) {
        return toResponse(findAddress(employeeId, addressId));
    }

    // ── Add address ───────────────────────────────────────────
    @Override
    @Transactional
    public EmployeeAddressResponse addAddress(Long employeeId, EmployeeAddressRequest request) {
        log.info("Adding {} address for employee {}", request.getAddressType(), employeeId);
        lockEmployee(employeeId);

        // Each type can only have one active address
        if (addressRepository.existsByEmployeeIdAndAddressTypeAndIsActive(
                employeeId, request.getAddressType(), 1)) {
            throw new BusinessRuleException(
                "DUPLICATE_ADDRESS_TYPE",
                "An active " + request.getAddressType() + " address already exists. " +
                "Please deactivate it before adding a new one.");
        }

        // If this is primary or it's the first address — set as primary
        boolean setPrimary = Boolean.TRUE.equals(request.getIsPrimary()) ||
            addressRepository.countByEmployeeIdAndIsActive(employeeId, 1) == 0;

        if (setPrimary) {
            addressRepository.unsetAllPrimary(employeeId);
        }

        EmployeeAddress entity = EmployeeAddress.builder()
            .employeeId(employeeId)
            .addressType(request.getAddressType())
            .addressLine1(request.getAddressLine1().trim())
            .addressLine2(request.getAddressLine2())
            .city(request.getCity().trim())
            .stateProvince(request.getStateProvince())
            .country(request.getCountry() != null ? request.getCountry().trim() : "South Sudan")
            .postalCode(request.getPostalCode())
            .isPrimary(setPrimary ? 1 : 0)
            .isActive(1)
            .build();

        EmployeeAddress saved = addressRepository.save(entity);
        log.info("Address added. ID: {}", saved.getEmployeeAddressesId());
        audit.record("EMPLOYEE_ADDRESS_ADDED", TARGET, saved.getEmployeeAddressesId(), summary(saved));
        return toResponse(saved);
    }

    // ── Update address ────────────────────────────────────────
    @Override
    @Transactional
    public EmployeeAddressResponse updateAddress(Long employeeId, Long addressId,
                                                  EmployeeAddressRequest request) {
        log.info("Updating address {} for employee {}", addressId, employeeId);
        EmployeeAddress existing = findAddress(employeeId, addressId);
        requireActive(existing);
        Versions.requireCurrent(request.getVersion(), existing.getVersion());

        // If type is changing, check no duplicate
        if (!existing.getAddressType().equals(request.getAddressType())) {
            if (addressRepository.existsByEmployeeIdAndAddressTypeAndIsActiveAndEmployeeAddressesIdNot(
                    employeeId, request.getAddressType(), 1, addressId)) {
                throw new BusinessRuleException(
                    "DUPLICATE_ADDRESS_TYPE",
                    "An active " + request.getAddressType() + " address already exists.");
            }
        }

        existing.setAddressType(request.getAddressType());
        existing.setAddressLine1(request.getAddressLine1().trim());
        existing.setAddressLine2(request.getAddressLine2());
        existing.setCity(request.getCity().trim());
        existing.setStateProvince(request.getStateProvince());
        existing.setCountry(request.getCountry() != null ? request.getCountry().trim() : "South Sudan");
        existing.setPostalCode(request.getPostalCode());
        existing.setUpdatedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.getIsPrimary()) && existing.getIsPrimary() != 1) {
            addressRepository.unsetAllPrimary(employeeId);
            existing.setIsPrimary(1);
        }

        EmployeeAddress updated = addressRepository.save(existing);
        audit.record("EMPLOYEE_ADDRESS_UPDATED", TARGET, addressId, summary(updated));
        return toResponse(updated);
    }

    // ── Set primary ───────────────────────────────────────────
    @Override
    @Transactional
    public void setPrimary(Long employeeId, Long addressId) {
        EmployeeAddress address = findAddress(employeeId, addressId);
        requireActive(address);
        addressRepository.unsetAllPrimary(employeeId);
        address.setIsPrimary(1);
        address.setUpdatedAt(LocalDateTime.now());
        addressRepository.save(address);
        log.info("Address {} set as primary for employee {}", addressId, employeeId);
        audit.record("EMPLOYEE_ADDRESS_PRIMARY_SET", TARGET, addressId, summary(address));
    }

    // ── Deactivate ────────────────────────────────────────────
    @Override
    @Transactional
    public void deactivateAddress(Long employeeId, Long addressId) {
        lockEmployee(employeeId);
        EmployeeAddress address = findAddress(employeeId, addressId);
        if (!isActive(address)) {
            return; // already removed: a repeated request changes nothing and is not audited again
        }

        // Cannot deactivate the only active address
        long activeCount = addressRepository.countByEmployeeIdAndIsActive(employeeId, 1);
        if (activeCount <= 1) {
            throw new BusinessRuleException(
                "CANNOT_DELETE_LAST",
                "Cannot deactivate the only active address.");
        }

        boolean wasPrimary = address.getIsPrimary() != null && address.getIsPrimary() == 1;

        address.setIsActive(0);
        address.setIsPrimary(0);
        address.setUpdatedAt(LocalDateTime.now());
        addressRepository.save(address);

        // Never leave an employee with active addresses but no primary one.
        if (wasPrimary) {
            addressRepository.findByEmployeeIdAndIsActiveOrderByAddressTypeAsc(employeeId, 1)
                .stream()
                .filter(a -> !a.getEmployeeAddressesId().equals(addressId))
                .findFirst()
                .ifPresent(next -> {
                    next.setIsPrimary(1);
                    next.setUpdatedAt(LocalDateTime.now());
                    addressRepository.save(next);
                    log.info("Address {} promoted to primary for employee {}",
                        next.getEmployeeAddressesId(), employeeId);
                });
        }
        log.info("Address {} deactivated for employee {}", addressId, employeeId);
        audit.record("EMPLOYEE_ADDRESS_DEACTIVATED", TARGET, addressId, summary(address));
    }

    // ── Helpers ───────────────────────────────────────────────

    /** Serialises changes to one employee's addresses: two requests cannot both pass the "last active" or duplicate-type check. */
    private void lockEmployee(Long employeeId) {
        employeeRepository.findByIdForUpdate(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
    }

    private static boolean isActive(EmployeeAddress a) {
        return a.getIsActive() != null && a.getIsActive() == 1;
    }

    /** A removed address is history: it cannot be edited or made the primary one. */
    private static void requireActive(EmployeeAddress a) {
        if (!isActive(a)) {
            throw new BusinessRuleException("ADDRESS_INACTIVE", "A removed address cannot be changed.");
        }
    }

    /** Who and which kind only: the address itself is personal data and stays out of the audit trail. */
    private static String summary(EmployeeAddress a) {
        return "employeeId=" + a.getEmployeeId() + ", type=" + a.getAddressType();
    }

    private EmployeeAddress findAddress(Long employeeId, Long addressId) {
        EmployeeAddress address = addressRepository.findById(addressId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
        if (!address.getEmployeeId().equals(employeeId)) {
            throw new ResourceNotFoundException("Address", "id", addressId);
        }
        return address;
    }

    private EmployeeAddressResponse toResponse(EmployeeAddress a) {
        return EmployeeAddressResponse.builder()
            .employeeAddressesId(a.getEmployeeAddressesId())
            .employeeId(a.getEmployeeId())
            .addressType(a.getAddressType())
            .addressLine1(a.getAddressLine1())
            .addressLine2(a.getAddressLine2())
            .city(a.getCity())
            .stateProvince(a.getStateProvince())
            .country(a.getCountry())
            .postalCode(a.getPostalCode())
            .isPrimary(a.getIsPrimary() == 1)
            .isActive(a.getIsActive() == 1)
            .createdAt(a.getCreatedAt() != null ? a.getCreatedAt().toString() : null)
            .version(a.getVersion())
            .createdBy(a.getCreatedBy())
            .updatedBy(a.getUpdatedBy())
            .updatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null)
            .build();
    }
}
