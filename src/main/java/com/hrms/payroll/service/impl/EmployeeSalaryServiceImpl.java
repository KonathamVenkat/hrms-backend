package com.hrms.payroll.service.impl;

import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse.SalaryBreakdownItem;
import com.hrms.payroll.entity.EmployeeSalary;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.entity.SalaryStructureItem;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.EmployeeSalaryRepository;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.SalaryStructureItemRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import com.hrms.payroll.service.EmployeeSalaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeSalaryServiceImpl implements EmployeeSalaryService {

    private final EmployeeSalaryRepository      salaryRepo;
    private final SalaryStructureRepository     structureRepo;
    private final SalaryStructureItemRepository itemRepo;
    private final SalaryComponentRepository     componentRepo;
    private final EmployeeRepository            employeeRepo;
    private final EmployeeJobDetailsRepository  jobDetailsRepo;

    // ────────────────────────────────────────────────────
    // ASSIGN SALARY STRUCTURE TO EMPLOYEE
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public EmployeeSalaryResponse assign(EmployeeSalaryRequest request) {
        log.info("Assigning salary structure — employeeId={}, structureId={}",
                request.employeeId(), request.structureId());

        Employee employee = findEmployee(request.employeeId());
        SalaryStructure structure = findStructure(request.structureId());

        LocalDate effectiveFrom = LocalDate.parse(request.effectiveFrom());

        // Deactivate previous salary record if exists
        salaryRepo.findByEmployeeIdAndIsCurrent(request.employeeId(), 1)
                .ifPresent(existing -> {
                    log.info("Deactivating previous salary record id={}",
                            existing.getEmpSalaryId());
                    salaryRepo.deactivateCurrent(request.employeeId());
                });

        // ── Calculate gross and net from structure ──────
        List<SalaryStructureItem> items = itemRepo
                .findByStructureIdAndIsActiveOrderBySortOrderAsc(
                        structure.getStructureId(), 1);

        Map<Long, SalaryComponent> componentMap = componentRepo
                .findAllById(items.stream()
                        .map(SalaryStructureItem::getComponentId)
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(SalaryComponent::getComponentId, c -> c));

        SalaryCalculation calc = calculateSalary(request.basicSalary(), items, componentMap);

        log.info("Calculated salary — basic={}, gross={}, net={}",
                request.basicSalary(), calc.grossSalary(), calc.netSalary());

        Long newId = salaryRepo.findNextSequenceValue();
        EmployeeSalary empSalary = EmployeeSalary.builder()
                .empSalaryId(newId)
                .employeeId(employee.getId())
                .structureId(structure.getStructureId())
                .basicSalary(request.basicSalary())
                .grossSalary(calc.grossSalary())
                .netSalary(calc.netSalary())
                .currency("OMR")
                .effectiveFrom(effectiveFrom)
                .isCurrent(1)
                .remarks(request.remarks())
                .build();

        empSalary.setCreatedBy(employee.getEmployeeCode());
        empSalary.setCreatedAt(LocalDateTime.now());
        empSalary.setUpdatedAt(LocalDateTime.now());

        EmployeeSalary saved = salaryRepo.save(empSalary);
        log.info("Salary assigned — empSalaryId={}", saved.getEmpSalaryId());

        return buildResponse(saved, employee, structure, items, componentMap);
    }

    // ────────────────────────────────────────────────────
    // READ
    // ────────────────────────────────────────────────────
    @Override
    public EmployeeSalaryResponse getCurrentSalary(Long employeeId) {
        log.info("Fetching current salary — employeeId={}", employeeId);
        Employee employee = findEmployee(employeeId);
        EmployeeSalary empSalary = salaryRepo
                .findByEmployeeIdAndIsCurrent(employeeId, 1)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeeSalary", "employeeId", employeeId));

        SalaryStructure structure = findStructure(empSalary.getStructureId());
        List<SalaryStructureItem> items = itemRepo
                .findByStructureIdAndIsActiveOrderBySortOrderAsc(
                        empSalary.getStructureId(), 1);
        Map<Long, SalaryComponent> componentMap = buildComponentMap(items);

        return buildResponse(empSalary, employee, structure, items, componentMap);
    }

    @Override
    public List<EmployeeSalaryResponse> getSalaryHistory(Long employeeId) {
        log.info("Fetching salary history — employeeId={}", employeeId);
        Employee employee = findEmployee(employeeId);
        List<EmployeeSalary> history = salaryRepo
                .findByEmployeeIdOrderByEffectiveFromDesc(employeeId);

        return history.stream().map(empSalary -> {
            SalaryStructure structure = findStructure(empSalary.getStructureId());
            List<SalaryStructureItem> items = itemRepo
                    .findByStructureIdAndIsActiveOrderBySortOrderAsc(
                            empSalary.getStructureId(), 1);
            Map<Long, SalaryComponent> componentMap = buildComponentMap(items);
            return buildResponse(empSalary, employee, structure, items, componentMap);
        }).collect(Collectors.toList());
    }

    @Override
    public PagedResponse<EmployeeSalaryResponse> getAllCurrent(
            Long structureId, Pageable pageable) {
        log.info("Fetching all current salaries — structureId={}", structureId);
        var page = salaryRepo.findCurrentByFilters(structureId, pageable);
        return PagedResponse.from(page.map(empSalary -> {
            Employee employee = findEmployee(empSalary.getEmployeeId());
            SalaryStructure structure = findStructure(empSalary.getStructureId());
            List<SalaryStructureItem> items = itemRepo
                    .findByStructureIdAndIsActiveOrderBySortOrderAsc(
                            empSalary.getStructureId(), 1);
            Map<Long, SalaryComponent> componentMap = buildComponentMap(items);
            return buildResponse(empSalary, employee, structure, items, componentMap);
        }));
    }

    // ── Salary Calculation ────────────────────────────────

    /**
     * Computes gross and net salary given a basic amount and structure items.
     * Oman payroll rules:
     *  - Earnings (FIXED/PERCENTAGE_OF_BASIC) → add to gross
     *  - PASI Employee (7% + 1% ILC) → deducted from net
     *  - Absence/Advance deductions → zero at assignment time (applied during payroll run)
     *  - No income tax in Oman
     */
    private SalaryCalculation calculateSalary(
            BigDecimal basicSalary,
            List<SalaryStructureItem> items,
            Map<Long, SalaryComponent> componentMap) {

        BigDecimal totalEarnings   = basicSalary; // basic is always an earning
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalStatutory  = BigDecimal.ZERO;

        for (SalaryStructureItem item : items) {
            SalaryComponent comp = componentMap.get(item.getComponentId());
            if (comp == null) continue;
            if ("BASIC".equals(comp.getComponentCode())) continue; // already included

            CalculationType calcType = item.getCalcTypeOverride() != null
                    ? item.getCalcTypeOverride() : comp.getCalcType();

            BigDecimal componentAmount = switch (calcType) {
                case FIXED               -> item.getAmount();
                case PERCENTAGE_OF_BASIC -> basicSalary
                        .multiply(item.getPercentage())
                        .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
                case PERCENTAGE_OF_GROSS -> BigDecimal.ZERO; // computed after gross is known
                case FORMULA             -> BigDecimal.ZERO; // computed at payroll run
            };

            switch (comp.getComponentType()) {
                case EARNING   -> totalEarnings   = totalEarnings.add(componentAmount);
                case DEDUCTION -> totalDeductions = totalDeductions.add(componentAmount);
                case STATUTORY -> totalStatutory  = totalStatutory.add(componentAmount);
            }
        }

        // Now compute PERCENTAGE_OF_GROSS items (like PASI if based on gross)
        BigDecimal grossSalary = totalEarnings;
        for (SalaryStructureItem item : items) {
            SalaryComponent comp = componentMap.get(item.getComponentId());
            if (comp == null) continue;
            CalculationType calcType = item.getCalcTypeOverride() != null
                    ? item.getCalcTypeOverride() : comp.getCalcType();

            if (calcType == CalculationType.PERCENTAGE_OF_GROSS) {
                BigDecimal amt = grossSalary
                        .multiply(item.getPercentage())
                        .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
                if (comp.getComponentType() == ComponentType.STATUTORY) {
                    totalStatutory = totalStatutory.add(amt);
                } else if (comp.getComponentType() == ComponentType.DEDUCTION) {
                    totalDeductions = totalDeductions.add(amt);
                }
            }
        }

        BigDecimal netSalary = grossSalary
                .subtract(totalDeductions)
                .subtract(totalStatutory)
                .setScale(3, RoundingMode.HALF_UP);

        return new SalaryCalculation(
                grossSalary.setScale(3, RoundingMode.HALF_UP),
                netSalary);
    }

    private record SalaryCalculation(BigDecimal grossSalary, BigDecimal netSalary) {}

    // ── Response builder ──────────────────────────────────

    private EmployeeSalaryResponse buildResponse(
            EmployeeSalary empSalary,
            Employee employee,
            SalaryStructure structure,
            List<SalaryStructureItem> items,
            Map<Long, SalaryComponent> componentMap) {

        // Resolve job details for designation/department
        EmployeeJobDetails jd = jobDetailsRepo
                .findByEmployeeIdAndIsCurrent(employee.getId(), 1)
                .orElse(null);

        String designationName = null;
        String departmentName  = null;
        // Note: jd.getDesignationId() → look up designation name if needed

        List<SalaryBreakdownItem> breakdown = buildBreakdown(
                empSalary.getBasicSalary(), items, componentMap);

        return new EmployeeSalaryResponse(
                empSalary.getEmpSalaryId(),
                empSalary.getEmployeeId(),
                employee.getEmployeeCode(),
                employee.getFirstName() + " " + employee.getLastName(),
                designationName,
                departmentName,
                empSalary.getStructureId(),
                structure.getStructureName(),
                empSalary.getBasicSalary(),
                empSalary.getGrossSalary(),
                empSalary.getNetSalary(),
                empSalary.getCurrency(),
                empSalary.getEffectiveFrom(),
                empSalary.getEffectiveTo(),
                empSalary.getIsCurrent() == 1,
                empSalary.getRemarks(),
                breakdown,
                empSalary.getCreatedAt(),
                empSalary.getUpdatedAt());
    }

    private List<SalaryBreakdownItem> buildBreakdown(
            BigDecimal basicSalary,
            List<SalaryStructureItem> items,
            Map<Long, SalaryComponent> componentMap) {

        List<SalaryBreakdownItem> breakdown = new ArrayList<>();

        // Basic salary always first
        breakdown.add(new SalaryBreakdownItem(
                "BASIC", "Basic Salary", "الراتب الأساسي",
                "EARNING", basicSalary, "Fixed"));

        for (SalaryStructureItem item : items) {
            SalaryComponent comp = componentMap.get(item.getComponentId());
            if (comp == null || "BASIC".equals(comp.getComponentCode())) continue;

            CalculationType calcType = item.getCalcTypeOverride() != null
                    ? item.getCalcTypeOverride() : comp.getCalcType();

            BigDecimal amount = switch (calcType) {
                case FIXED               -> item.getAmount();
                case PERCENTAGE_OF_BASIC -> basicSalary
                        .multiply(item.getPercentage())
                        .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
                case PERCENTAGE_OF_GROSS -> BigDecimal.ZERO; // shown as 0 at assignment
                case FORMULA             -> BigDecimal.ZERO;
            };

            String calcBasis = switch (calcType) {
                case FIXED               -> "Fixed";
                case PERCENTAGE_OF_BASIC -> item.getPercentage() + "% of Basic";
                case PERCENTAGE_OF_GROSS -> item.getPercentage() + "% of Gross";
                case FORMULA             -> "Computed at payroll run";
            };

            breakdown.add(new SalaryBreakdownItem(
                    comp.getComponentCode(), comp.getComponentName(),
                    comp.getComponentNameAr(), comp.getComponentType().name(),
                    amount, calcBasis));
        }

        return breakdown;
    }

    private Map<Long, SalaryComponent> buildComponentMap(List<SalaryStructureItem> items) {
        return componentRepo.findAllById(
                items.stream().map(SalaryStructureItem::getComponentId)
                        .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(SalaryComponent::getComponentId, c -> c));
    }

    private Employee findEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Employee not found — id={}", id);
                    return new ResourceNotFoundException("Employee", "id", id);
                });
    }

    private SalaryStructure findStructure(Long id) {
        return structureRepo.findById(id)
                .orElseThrow(() -> {
                    log.error("Salary structure not found — id={}", id);
                    return new ResourceNotFoundException("SalaryStructure", "id", id);
                });
    }
}
