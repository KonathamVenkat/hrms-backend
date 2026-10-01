package com.hrms.employee.service.impl;

import com.hrms.common.audit.CurrentAuditor;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.OfficeLocationRequest;
import com.hrms.employee.dto.response.OfficeLocationResponse;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.employee.service.OfficeLocationService;
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
public class OfficeLocationServiceImpl implements OfficeLocationService {

    private final OfficeLocationRepository locationRepository;

    @Override
    public List<OfficeLocationResponse> getAllLocations() {
        return locationRepository.findAllByOrderBySortOrderAsc()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<OfficeLocationResponse> getActiveLocations() {
        return locationRepository.findByIsActiveOrderBySortOrderAsc(1)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public OfficeLocationResponse getLocationById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional
    public OfficeLocationResponse createLocation(OfficeLocationRequest request) {
        log.info("Creating office location: {}", request.getLocationCode());

        if (locationRepository.existsByLocationCodeIgnoreCase(request.getLocationCode())) {
            throw new DuplicateResourceException("OfficeLocation", "code", request.getLocationCode());
        }
        if (locationRepository.existsByLocationNameIgnoreCase(request.getLocationName())) {
            throw new DuplicateResourceException("OfficeLocation", "name", request.getLocationName());
        }

        OfficeLocation entity = OfficeLocation.builder()
            .locationCode(request.getLocationCode().toUpperCase().trim())
            .locationName(request.getLocationName().trim())
            .locationNameAr(request.getLocationNameAr().trim())
            .locationType(request.getLocationType().toUpperCase())
            .addressLine1(request.getAddressLine1().trim())
            .addressLine2(request.getAddressLine2())
            .city(request.getCity().trim())
            .stateProvince(request.getStateProvince())
            .country(request.getCountry() != null ? request.getCountry().trim() : "Oman")
            .postalCode(request.getPostalCode())
            .phone(request.getPhone())
            .email(request.getEmail())
            .timezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Muscat")
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .isActive(request.getIsActive() != null ? boolToInt(request.getIsActive()) : 1)
            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
            .createdBy(CurrentAuditor.name())
            .createdAt(LocalDateTime.now())
            .build();

        OfficeLocation saved = locationRepository.save(entity);
        log.info("Office location created. ID: {}", saved.getLocationId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public OfficeLocationResponse updateLocation(Long id, OfficeLocationRequest request) {
        log.info("Updating office location id: {}", id);
        OfficeLocation existing = findById(id);

        if (locationRepository.existsByLocationCodeIgnoreCaseAndLocationIdNot(
                request.getLocationCode(), id)) {
            throw new DuplicateResourceException("OfficeLocation", "code", request.getLocationCode());
        }
        if (locationRepository.existsByLocationNameIgnoreCaseAndLocationIdNot(
                request.getLocationName(), id)) {
            throw new DuplicateResourceException("OfficeLocation", "name", request.getLocationName());
        }

        existing.setLocationCode(request.getLocationCode().toUpperCase().trim());
        existing.setLocationName(request.getLocationName().trim());
        existing.setLocationNameAr(request.getLocationNameAr().trim());
        existing.setLocationType(request.getLocationType().toUpperCase());
        existing.setAddressLine1(request.getAddressLine1().trim());
        existing.setAddressLine2(request.getAddressLine2());
        existing.setCity(request.getCity().trim());
        existing.setStateProvince(request.getStateProvince());
        existing.setCountry(request.getCountry() != null ? request.getCountry().trim() : "Oman");
        existing.setPostalCode(request.getPostalCode());
        existing.setPhone(request.getPhone());
        existing.setEmail(request.getEmail());
        existing.setTimezone(request.getTimezone() != null ? request.getTimezone() : "Asia/Muscat");
        existing.setLatitude(request.getLatitude());
        existing.setLongitude(request.getLongitude());
        existing.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.getIsActive() != null) existing.setIsActive(boolToInt(request.getIsActive()));
        existing.setUpdatedBy(CurrentAuditor.name());
        existing.setUpdatedAt(LocalDateTime.now());

        return toResponse(locationRepository.save(existing));
    }

    @Override @Transactional
    public void deactivateLocation(Long id) {
        OfficeLocation loc = findById(id);
        loc.setIsActive(0); loc.setUpdatedBy(CurrentAuditor.name()); loc.setUpdatedAt(LocalDateTime.now());
        locationRepository.save(loc);
    }

    @Override @Transactional
    public void activateLocation(Long id) {
        OfficeLocation loc = locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OfficeLocation", "id", id));
        loc.setIsActive(1); loc.setUpdatedBy(CurrentAuditor.name()); loc.setUpdatedAt(LocalDateTime.now());
        locationRepository.save(loc);
    }

    private OfficeLocation findById(Long id) {
        return locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("OfficeLocation", "id", id));
    }

    private OfficeLocationResponse toResponse(OfficeLocation loc) {
        return OfficeLocationResponse.builder()
            .locationId(loc.getLocationId())
            .locationCode(loc.getLocationCode())
            .locationName(loc.getLocationName())
            .locationNameAr(loc.getLocationNameAr())
            .locationType(loc.getLocationType())
            .addressLine1(loc.getAddressLine1())
            .addressLine2(loc.getAddressLine2())
            .city(loc.getCity())
            .stateProvince(loc.getStateProvince())
            .country(loc.getCountry())
            .postalCode(loc.getPostalCode())
            .phone(loc.getPhone())
            .email(loc.getEmail())
            .timezone(loc.getTimezone())
            .latitude(loc.getLatitude())
            .longitude(loc.getLongitude())
            .isActive(loc.getIsActive() == 1)
            .sortOrder(loc.getSortOrder())
            .createdBy(loc.getCreatedBy())
            .createdAt(loc.getCreatedAt() != null ? loc.getCreatedAt().toString() : null)
            .updatedAt(loc.getUpdatedAt() != null ? loc.getUpdatedAt().toString() : null)
            .build();
    }

    private int boolToInt(Boolean b) { return Boolean.TRUE.equals(b) ? 1 : 0; }
}

