package com.hrms.payroll.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.payroll.dto.request.SalaryComponentRequest;
import com.hrms.payroll.dto.response.SalaryComponentResponse;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.service.SalaryComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryComponentServiceImpl implements SalaryComponentService {

    private final SalaryComponentRepository componentRepo;

    @Override
    public List<SalaryComponentResponse> getAll(Boolean activeOnly) {
        log.info("Fetching salary components — activeOnly={}", activeOnly);
        List<SalaryComponent> list = Boolean.TRUE.equals(activeOnly)
                ? componentRepo.findByIsActiveOrderBySortOrderAsc(1)
                : componentRepo.findAll();
        log.info("Found {} salary components", list.size());
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<SalaryComponentResponse> getByType(ComponentType type) {
        log.info("Fetching salary components by type={}", type);
        return componentRepo
                .findByComponentTypeAndIsActiveOrderBySortOrderAsc(type, 1)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public SalaryComponentResponse getById(Long id) {
        log.info("Fetching salary component id={}", id);
        return toResponse(findComponent(id));
    }

    @Override
    @Transactional
    public SalaryComponentResponse create(SalaryComponentRequest request) {
        log.info("Creating salary component — code={}", request.componentCode());

        if (componentRepo.existsByComponentCode(request.componentCode())) {
            throw new BusinessRuleException(
                    "Salary component with code '" + request.componentCode() + "' already exists.");
        }
        if (componentRepo.existsByComponentName(request.componentName())) {
            throw new BusinessRuleException(
                    "Salary component with name '" + request.componentName() + "' already exists.");
        }

        Long id = componentRepo.findNextSequenceValue();
        SalaryComponent comp = SalaryComponent.builder()
                .componentId(id)
                .componentCode(request.componentCode().toUpperCase().trim())
                .componentName(request.componentName().trim())
                .componentNameAr(request.componentNameAr().trim())
                .componentType(request.componentType())
                .calcType(request.calcType())
                .defaultValue(request.defaultValue() != null
                        ? request.defaultValue() : BigDecimal.ZERO)
                .isTaxable(Boolean.TRUE.equals(request.isTaxable()) ? 1 : 0)
                .isPasiApplicable(Boolean.TRUE.equals(request.isPasiApplicable()) ? 1 : 0)
                .description(request.description())
                .sortOrder(request.sortOrder() != null ? request.sortOrder() : 0)
                .build();

        comp.setCreatedAt(LocalDateTime.now());
        comp.setUpdatedAt(LocalDateTime.now());

        SalaryComponent saved = componentRepo.save(comp);
        log.info("Salary component created — id={}, code={}", saved.getComponentId(),
                saved.getComponentCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SalaryComponentResponse update(Long id, SalaryComponentRequest request) {
        log.info("Updating salary component id={}", id);
        SalaryComponent comp = findComponent(id);

        // Check duplicate code only if changed
        if (!comp.getComponentCode().equals(request.componentCode().toUpperCase())
                && componentRepo.existsByComponentCode(request.componentCode())) {
            throw new BusinessRuleException(
                    "Component code '" + request.componentCode() + "' already exists.");
        }

        comp.setComponentCode(request.componentCode().toUpperCase().trim());
        comp.setComponentName(request.componentName().trim());
        comp.setComponentNameAr(request.componentNameAr().trim());
        comp.setComponentType(request.componentType());
        comp.setCalcType(request.calcType());
        comp.setDefaultValue(request.defaultValue() != null
                ? request.defaultValue() : BigDecimal.ZERO);
        comp.setIsTaxable(Boolean.TRUE.equals(request.isTaxable()) ? 1 : 0);
        comp.setIsPasiApplicable(Boolean.TRUE.equals(request.isPasiApplicable()) ? 1 : 0);
        comp.setDescription(request.description());
        if (request.sortOrder() != null) comp.setSortOrder(request.sortOrder());
        comp.setUpdatedAt(LocalDateTime.now());

        log.info("Salary component updated — id={}", id);
        return toResponse(componentRepo.save(comp));
    }

    @Override
    @Transactional
    public void toggleActive(Long id, boolean active) {
        log.info("Toggling salary component id={} active={}", id, active);
        SalaryComponent comp = findComponent(id);
        comp.setIsActive(active ? 1 : 0);
        comp.setUpdatedAt(LocalDateTime.now());
        componentRepo.save(comp);
    }

    // ── Helper ────────────────────────────────────────────
    private SalaryComponent findComponent(Long id) {
        return componentRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Salary component not found — id={}", id);
                    return new ResourceNotFoundException("SalaryComponent", "id", id);
                });
    }

    SalaryComponentResponse toResponse(SalaryComponent c) {
        String typeLabel = switch (c.getComponentType()) {
            case EARNING   -> "Earning";
            case DEDUCTION -> "Deduction";
            case STATUTORY -> "Statutory";
        };
        String calcLabel = switch (c.getCalcType()) {
            case FIXED                -> "Fixed Amount";
            case PERCENTAGE_OF_BASIC  -> "% of Basic";
            case PERCENTAGE_OF_GROSS  -> "% of Gross";
            case FORMULA              -> "Formula";
        };
        return new SalaryComponentResponse(
                c.getComponentId(), c.getComponentCode(),
                c.getComponentName(), c.getComponentNameAr(),
                c.getComponentType(), typeLabel,
                c.getCalcType(), calcLabel,
                c.getDefaultValue(),
                c.getIsTaxable() == 1,
                c.getIsPasiApplicable() == 1,
                c.getDescription(),
                c.getSortOrder(),
                c.getIsActive() == 1,
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
