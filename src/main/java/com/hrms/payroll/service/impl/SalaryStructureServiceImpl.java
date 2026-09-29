package com.hrms.payroll.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.payroll.dto.request.SalaryStructureItemRequest;
import com.hrms.payroll.dto.request.SalaryStructureRequest;
import com.hrms.payroll.dto.response.SalaryStructureItemResponse;
import com.hrms.payroll.dto.response.SalaryStructureResponse;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.entity.SalaryStructureItem;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.SalaryStructureItemRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import com.hrms.payroll.service.SalaryStructureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryStructureServiceImpl implements SalaryStructureService {

    private final SalaryStructureRepository     structureRepo;
    private final SalaryStructureItemRepository itemRepo;
    private final SalaryComponentRepository     componentRepo;

    @Override
    public List<SalaryStructureResponse> getAll(Boolean activeOnly) {
        log.info("Fetching salary structures — activeOnly={}", activeOnly);
        List<SalaryStructure> list = Boolean.TRUE.equals(activeOnly)
                ? structureRepo.findByIsActiveOrderByStructureNameAsc(1)
                : structureRepo.findAll();
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public SalaryStructureResponse getById(Long id) {
        log.info("Fetching salary structure id={}", id);
        return toResponse(findStructure(id));
    }

    @Override
    @Transactional
    public SalaryStructureResponse create(SalaryStructureRequest request) {
        log.info("Creating salary structure — code={}", request.structureCode());

        if (structureRepo.existsByStructureCode(request.structureCode().toUpperCase().trim())) {
            throw new BusinessRuleException(
                    "Structure with code '" + request.structureCode() + "' already exists.");
        }
        if (structureRepo.existsByStructureName(request.structureName())) {
            throw new BusinessRuleException(
                    "Structure with name '" + request.structureName() + "' already exists.");
        }

        Long structureId = structureRepo.findNextSequenceValue();
        SalaryStructure structure = SalaryStructure.builder()
                .structureId(structureId)
                .structureCode(request.structureCode().toUpperCase().trim())
                .structureName(request.structureName().trim())
                .description(request.description())
                .build();

        structure.setCreatedAt(LocalDateTime.now());
        structure.setUpdatedAt(LocalDateTime.now());
        structureRepo.save(structure);

        // Save items
        saveItems(structureId, request.items());

        log.info("Salary structure created — id={}", structureId);
        return toResponse(findStructure(structureId));
    }

    @Override
    @Transactional
    public SalaryStructureResponse update(Long id, SalaryStructureRequest request) {
        log.info("Updating salary structure id={}", id);
        SalaryStructure structure = findStructure(id);

        // A structure's items feed every EmployeeSalary breakdown live (the totals are
        // frozen at assignment time, but the line-item breakdown is recomputed from the
        // current structure on every read) — editing an in-use structure would silently
        // rewrite the displayed breakdown for past and present assignments alike.
        int empCount = structureRepo.countCurrentEmployeesByStructureId(id);
        if (empCount > 0) {
            throw new BusinessRuleException("STRUCTURE_IN_USE",
                    "Cannot edit '" + structure.getStructureName() + "' — it is currently assigned to "
                    + empCount + " employee(s). Deactivate it and create a new structure instead.");
        }

        structure.setStructureName(request.structureName().trim());
        structure.setDescription(request.description());
        structure.setUpdatedAt(LocalDateTime.now());
        structureRepo.save(structure);

        // Replace all items
        itemRepo.deleteByStructureId(id);
        saveItems(id, request.items());

        log.info("Salary structure updated — id={}", id);
        return toResponse(findStructure(id));
    }

    @Override
    @Transactional
    public void toggleActive(Long id, boolean active) {
        log.info("Toggling salary structure id={} active={}", id, active);
        SalaryStructure structure = findStructure(id);

        if (!active) {
            int empCount = structureRepo.countCurrentEmployeesByStructureId(id);
            if (empCount > 0) {
                throw new BusinessRuleException("STRUCTURE_IN_USE",
                        "Cannot deactivate '" + structure.getStructureName() + "' — it is currently assigned to "
                        + empCount + " employee(s).");
            }
        }

        structure.setIsActive(active ? 1 : 0);
        structure.setUpdatedAt(LocalDateTime.now());
        structureRepo.save(structure);
    }

    // ── Helpers ───────────────────────────────────────────

    private void saveItems(Long structureId, List<SalaryStructureItemRequest> items) {
        Set<Long> seenComponentIds = new HashSet<>();

        for (int i = 0; i < items.size(); i++) {
            SalaryStructureItemRequest req = items.get(i);

            // Validate component exists
            if (!componentRepo.existsById(req.componentId())) {
                throw new BusinessRuleException(
                        "Salary component not found: id=" + req.componentId());
            }
            if (!seenComponentIds.add(req.componentId())) {
                throw new BusinessRuleException(
                        "Component id=" + req.componentId() + " is added more than once to this structure.");
            }

            Long itemId = itemRepo.findNextSequenceValue();
            SalaryStructureItem item = SalaryStructureItem.builder()
                    .itemId(itemId)
                    .structureId(structureId)
                    .componentId(req.componentId())
                    .calcTypeOverride(req.calcTypeOverride())
                    .amount(req.amount() != null ? req.amount() : BigDecimal.ZERO)
                    .percentage(req.percentage() != null ? req.percentage() : BigDecimal.ZERO)
                    .sortOrder(req.sortOrder() != null ? req.sortOrder() : i + 1)
                    .build();

            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            itemRepo.save(item);
        }
        log.debug("Saved {} structure items for structureId={}", items.size(), structureId);
    }

    private SalaryStructure findStructure(Long id) {
        return structureRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Salary structure not found — id={}", id);
                    return new ResourceNotFoundException("SalaryStructure", "id", id);
                });
    }

    private SalaryStructureResponse toResponse(SalaryStructure s) {
        List<SalaryStructureItem> items = itemRepo
                .findByStructureIdAndIsActiveOrderBySortOrderAsc(s.getStructureId(), 1);

        // Pre-load components to avoid N+1
        Map<Long, SalaryComponent> componentMap = componentRepo.findAllById(
                items.stream().map(SalaryStructureItem::getComponentId)
                        .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(
                        SalaryComponent::getComponentId, c -> c));

        List<SalaryStructureItemResponse> itemResponses = items.stream().map(item -> {
            SalaryComponent comp = componentMap.get(item.getComponentId());
            return new SalaryStructureItemResponse(
                    item.getItemId(),
                    item.getComponentId(),
                    comp != null ? comp.getComponentCode() : "",
                    comp != null ? comp.getComponentName() : "",
                    comp != null ? comp.getComponentNameAr() : "",
                    comp != null ? comp.getComponentType() : null,
                    item.getCalcTypeOverride() != null
                            ? item.getCalcTypeOverride()
                            : (comp != null ? comp.getCalcType() : null),
                    item.getAmount(),
                    item.getPercentage(),
                    item.getSortOrder()
            );
        }).collect(Collectors.toList());

        int empCount = structureRepo.countCurrentEmployeesByStructureId(s.getStructureId());

        return new SalaryStructureResponse(
                s.getStructureId(), s.getStructureCode(),
                s.getStructureName(), s.getDescription(),
                s.getIsActive() == 1,
                itemResponses, empCount,
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
