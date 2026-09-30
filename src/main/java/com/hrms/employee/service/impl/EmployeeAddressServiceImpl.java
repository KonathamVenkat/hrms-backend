package com.hrms.employee.service.impl;

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

    // ── Get all addresses ─────────────────────────────────────
    @Override
    public List<EmployeeAddressResponse> getAddresses(Long employeeId) {
        validateEmployee(employeeId);
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
        validateEmployee(employeeId);

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
            .country(request.getCountry() != null ? request.getCountry().trim() : "Oman")
            .postalCode(request.getPostalCode())
            .isPrimary(setPrimary ? 1 : 0)
            .isActive(1)
            .createdAt(LocalDateTime.now())
            .build();

        EmployeeAddress saved = addressRepository.save(entity);
        log.info("Address added. ID: {}", saved.getEmployeeAddressesId());
        return toResponse(saved);
    }

    // ── Update address ────────────────────────────────────────
    @Override
    @Transactional
    public EmployeeAddressResponse updateAddress(Long employeeId, Long addressId,
                                                  EmployeeAddressRequest request) {
        log.info("Updating address {} for employee {}", addressId, employeeId);
        EmployeeAddress existing = findAddress(employeeId, addressId);

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
        existing.setCountry(request.getCountry() != null ? request.getCountry().trim() : "Oman");
        existing.setPostalCode(request.getPostalCode());
        existing.setUpdatedAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(request.getIsPrimary()) && existing.getIsPrimary() != 1) {
            addressRepository.unsetAllPrimary(employeeId);
            existing.setIsPrimary(1);
        }

        return toResponse(addressRepository.save(existing));
    }

    // ── Set primary ───────────────────────────────────────────
    @Override
    @Transactional
    public void setPrimary(Long employeeId, Long addressId) {
        EmployeeAddress address = findAddress(employeeId, addressId);
        addressRepository.unsetAllPrimary(employeeId);
        address.setIsPrimary(1);
        address.setUpdatedAt(LocalDateTime.now());
        addressRepository.save(address);
        log.info("Address {} set as primary for employee {}", addressId, employeeId);
    }

    // ── Deactivate ────────────────────────────────────────────
    @Override
    @Transactional
    public void deactivateAddress(Long employeeId, Long addressId) {
        EmployeeAddress address = findAddress(employeeId, addressId);

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
    }

    // ── Helpers ───────────────────────────────────────────────
    private void validateEmployee(Long employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee", "id", employeeId);
        }
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
            .updatedAt(a.getUpdatedAt() != null ? a.getUpdatedAt().toString() : null)
            .build();
    }
}
