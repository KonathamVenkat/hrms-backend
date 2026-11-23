package com.hrms.payroll.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.audit.CurrentAuditor;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.service.GradeService;
import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse.SalaryBreakdownItem;
import com.hrms.payroll.dto.response.PayScaleBasisResponse;
import com.hrms.payroll.entity.EmployeeSalary;
import com.hrms.payroll.entity.QualificationLevel;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.entity.SalaryStructureItem;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.EmployeeLoanRepository;
import com.hrms.payroll.repository.EmployeeSalaryRepository;
import com.hrms.payroll.repository.QualificationLevelRepository;
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
import java.util.Optional;
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
    private final DepartmentRepository          departmentRepo;
    private final DesignationRepository         designationRepo;
    private final EmployeeAccessGuard           employeeAccessGuard;
    private final GradeService                  grades;
    private final QualificationLevelRepository  qualificationLevels;
    private final EmployeeLoanRepository        loans;
    private final AuditTrail audit;

    /** Components of the per-employee allowances (step 4a); their amounts live on the salary, not in a structure. */
    static final String SOCIAL_ALLOWANCE = "SOCIAL_ALLOW";
    static final String QUALIFICATION_ALLOWANCE = "QUALIFICATION";

    // ────────────────────────────────────────────────────
    // ASSIGN SALARY STRUCTURE TO EMPLOYEE
    // ────────────────────────────────────────────────────
    @Override
    @Transactional
    public EmployeeSalaryResponse assign(EmployeeSalaryRequest request) {
        log.info("Assigning salary structure — employeeId={}, structureId={}",
                request.employeeId(), request.structureId());

        // Two simultaneous assignments for one employee would each close the old record and insert a current one.
        // The row lock makes the second wait and see the first; the unique index (V24) is the backstop.
        Employee employee = employeeRepo.findByIdForUpdate(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", request.employeeId()));
        // Lock order is employee, structure, components (structure and component edits take theirs in the same order),
        // so an edit or deactivation of what is being assigned waits until this assignment is committed.
        SalaryStructure structure = structureRepo.findByIdForUpdate(request.structureId())
                .orElseThrow(() -> new ResourceNotFoundException("SalaryStructure", "id", request.structureId()));

        if (structure.getIsActive() != 1) {
            throw new BusinessRuleException("STRUCTURE_INACTIVE",
                    "Salary structure '" + structure.getStructureName() + "' is no longer active.");
        }

        LocalDate effectiveFrom = LocalDate.parse(request.effectiveFrom());
        EmployeeJobDetails job = currentJob(employee.getId());
        BigDecimal basicSalary = resolveBasic(job, structure, request.basicSalary(), effectiveFrom);
        BigDecimal socialAmount = Boolean.TRUE.equals(request.socialAllowance()) ? socialAllowanceAmount() : null;
        QualificationLevel qualification = request.qualificationLevelId() == null
                ? null : activeQualificationLevel(request.qualificationLevelId());
        BigDecimal qualificationAmount = qualification == null ? null : qualification.getMonthlyAmount();

        // A new salary record must postdate whichever record it's replacing as "current" —
        // otherwise the history ends up with an earlier-dated record marked current.
        Optional<EmployeeSalary> previousCurrent =
                salaryRepo.findByEmployeeIdAndIsCurrent(request.employeeId(), 1);
        if (previousCurrent.isPresent()
                && !effectiveFrom.isAfter(previousCurrent.get().getEffectiveFrom())) {
            throw new BusinessRuleException("INVALID_EFFECTIVE_DATE",
                    "New effective date (" + effectiveFrom
                    + ") must be after the current salary's effective date ("
                    + previousCurrent.get().getEffectiveFrom() + ").");
        }

        // Close the previous record the day before this one starts: no gap, no overlap in the history.
        previousCurrent.ifPresent(existing -> {
            log.info("Deactivating previous salary record id={}",
                    existing.getEmpSalaryId());
            salaryRepo.deactivateCurrent(request.employeeId(), effectiveFrom.minusDays(1));
        });

        // ── Calculate gross and net from structure ──────
        List<SalaryStructureItem> items = itemRepo
                .findByStructureIdAndIsActiveOrderBySortOrderAsc(
                        structure.getStructureId(), 1);

        Map<Long, SalaryComponent> componentMap = componentRepo
                .findAllByIdForUpdate(items.stream()
                        .map(SalaryStructureItem::getComponentId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(SalaryComponent::getComponentId, c -> c));

        SalaryCalculation calc = calculateSalary(basicSalary, sum(socialAmount, qualificationAmount), items, componentMap);

        log.info("Calculated salary — basic={}, gross={}, net={}",
                basicSalary, calc.grossSalary(), calc.netSalary());

        audit.record("SALARY_ASSIGNED", "EMPLOYEE", employee.getId(),
            "structure " + structure.getStructureId() + ", effective " + effectiveFrom
                + (isGraded(job) ? ", basic from pay scale grade " + job.getGradeId() + " increment " + job.getIncrementNo() : "")
                + (socialAmount != null ? ", social allowance" : "")
                + (qualification != null ? ", qualification level " + qualification.getId() : ""));
        Long newId = salaryRepo.findNextSequenceValue();
        EmployeeSalary empSalary = EmployeeSalary.builder()
                .empSalaryId(newId)
                .employeeId(employee.getId())
                .structureId(structure.getStructureId())
                .basicSalary(basicSalary)
                .grossSalary(calc.grossSalary())
                .netSalary(calc.netSalary())
                .currency("SSP")
                .effectiveFrom(effectiveFrom)
                .isCurrent(1)
                .remarks(request.remarks())
                .socialAmount(socialAmount)
                .qualificationLevelId(qualification == null ? null : qualification.getId())
                .qualificationAmount(qualificationAmount)
                .build();

        empSalary.setCreatedBy(CurrentAuditor.name());
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
        // A salary is read by the employee it belongs to or by an HR_ADMIN: not by an HR_MANAGER.
        if (!employeeAccessGuard.isSelfOrAdmin(employeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A salary can only be read by its owner or an HR_ADMIN.");
        }

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

    // ── Pay scale ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PayScaleBasisResponse getPayScaleBasis(Long employeeId, LocalDate onDate) {
        findEmployee(employeeId);
        EmployeeJobDetails job = currentJob(employeeId);
        if (!isGraded(job)) {
            return new PayScaleBasisResponse(false, onDate, null, null, null, null, null, null, null);
        }
        Optional<GradeResponse> grade = grades.findGrade(job.getGradeId());
        Optional<SalaryStructure> structure = structureRepo.findByGradeId(job.getGradeId());
        return new PayScaleBasisResponse(true, onDate, job.getGradeId(),
                grade.map(GradeResponse::getName).orElse(null),
                grade.map(GradeResponse::getNameAr).orElse(null),
                job.getIncrementNo(),
                structure.map(SalaryStructure::getStructureId).orElse(null),
                structure.map(SalaryStructure::getStructureName).orElse(null),
                scaleBasic(job, onDate).orElse(null));
    }

    /**
     * The basic of the salary being assigned. Without a grade on the current job it is the one typed (required).
     * With a grade it is the pay-scale amount on the effective date, on the grade's own structure: a different
     * structure, a different typed amount, no linked structure or no scale amount on that date are refused.
     */
    private BigDecimal resolveBasic(EmployeeJobDetails job, SalaryStructure structure, BigDecimal requested,
                                    LocalDate effectiveFrom) {
        if (!isGraded(job)) {
            if (requested == null) {
                throw new BusinessRuleException("BASIC_REQUIRED",
                        "Enter the basic salary: this employee has no grade on the pay scale.");
            }
            return requested;
        }
        SalaryStructure gradeStructure = structureRepo.findByGradeId(job.getGradeId())
                .orElseThrow(() -> new BusinessRuleException("GRADE_STRUCTURE_MISSING",
                        "No salary structure is linked to this employee's grade yet."));
        if (!gradeStructure.getStructureId().equals(structure.getStructureId())) {
            throw new BusinessRuleException("STRUCTURE_GRADE_MISMATCH",
                    "This employee's grade is paid on structure '" + gradeStructure.getStructureName() + "'.");
        }
        BigDecimal basic = scaleBasic(job, effectiveFrom)
                .orElseThrow(() -> new BusinessRuleException("PAY_SCALE_MISSING",
                        "The pay scale has no amount for this employee's grade and increment on " + effectiveFrom + "."));
        if (requested != null && requested.compareTo(basic) != 0) {
            throw new BusinessRuleException("BASIC_NOT_FROM_SCALE",
                    "The basic salary of a graded employee comes from the pay scale and cannot be typed.");
        }
        return basic;
    }

    /** The Social Allowance component's current default, copied into the salary; it must be an active earning above 0. */
    private BigDecimal socialAllowanceAmount() {
        return componentRepo.findByComponentCode(SOCIAL_ALLOWANCE)
                .filter(c -> c.getIsActive() == 1 && c.getComponentType() == ComponentType.EARNING
                        && c.getDefaultValue() != null && c.getDefaultValue().signum() > 0)
                .map(c -> c.getDefaultValue().setScale(2, RoundingMode.HALF_UP))
                .orElseThrow(() -> new BusinessRuleException("SOCIAL_ALLOWANCE_UNAVAILABLE",
                        "The Social Allowance component is inactive or has no amount. Ask the payroll administrator."));
    }

    private QualificationLevel activeQualificationLevel(Long id) {
        QualificationLevel level = qualificationLevels.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Qualification level", "id", id));
        if (!Boolean.TRUE.equals(level.getIsActive())) {
            throw new BusinessRuleException("QUALIFICATION_LEVEL_INACTIVE", "The selected qualification level is inactive.");
        }
        return level;
    }

    private static BigDecimal sum(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).add(b == null ? BigDecimal.ZERO : b);
    }

    /** The column holds 2 decimals; the scale's amounts are whole numbers, so this never changes one. */
    private Optional<BigDecimal> scaleBasic(EmployeeJobDetails job, LocalDate onDate) {
        return grades.findStartingScale(job.getGradeId(), job.getIncrementNo(), onDate)
                .map(amount -> amount.setScale(2, RoundingMode.HALF_UP));
    }

    private EmployeeJobDetails currentJob(Long employeeId) {
        return jobDetailsRepo.findByEmployeeIdAndIsCurrent(employeeId, 1).orElse(null);
    }

    private static boolean isGraded(EmployeeJobDetails job) {
        return job != null && job.getGradeId() != null;
    }

    // ── Salary Calculation ────────────────────────────────

    /**
     * Computes gross and net salary given a basic amount and structure items.
     * Rules (rates come from the salary structure, not from code):
     *  - Earnings (FIXED/PERCENTAGE_OF_BASIC) → add to gross
     *  - Statutory contributions such as NSSF → deducted from net
     *  - Absence/Advance deductions → zero at assignment time (applied during payroll run)
     *  - Income tax is not calculated here
     */
    private SalaryCalculation calculateSalary(
            BigDecimal basicSalary,
            BigDecimal employeeEarnings,
            List<SalaryStructureItem> items,
            Map<Long, SalaryComponent> componentMap) {

        // Basic and the employee's own allowances (Social, Qualification) are always earnings, part of gross.
        BigDecimal totalEarnings   = basicSalary.add(employeeEarnings);
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

        // Now compute PERCENTAGE_OF_GROSS items (like NSSF if based on gross).
        // Gross-based EARNINGs are computed against this same baseline gross (not
        // fed back into it) to avoid a circular gross-depends-on-gross calculation.
        BigDecimal grossSalary = totalEarnings;
        BigDecimal additionalGrossEarnings = BigDecimal.ZERO;
        for (SalaryStructureItem item : items) {
            SalaryComponent comp = componentMap.get(item.getComponentId());
            if (comp == null) continue;
            CalculationType calcType = item.getCalcTypeOverride() != null
                    ? item.getCalcTypeOverride() : comp.getCalcType();

            if (calcType == CalculationType.PERCENTAGE_OF_GROSS) {
                BigDecimal amt = grossSalary
                        .multiply(item.getPercentage())
                        .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
                switch (comp.getComponentType()) {
                    case STATUTORY -> totalStatutory = totalStatutory.add(amt);
                    case DEDUCTION -> totalDeductions = totalDeductions.add(amt);
                    case EARNING   -> additionalGrossEarnings = additionalGrossEarnings.add(amt);
                }
            }
        }
        grossSalary = grossSalary.add(additionalGrossEarnings);

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

        String designationName = jd != null
                ? designationRepo.findById(jd.getDesignationId())
                        .map(Designation::getTitle).orElse(null)
                : null;
        String departmentName = jd != null
                ? departmentRepo.findById(jd.getDepartmentId())
                        .map(Department::getName).orElse(null)
                : null;

        List<SalaryBreakdownItem> breakdown = buildBreakdown(
                empSalary.getBasicSalary(), empSalary.getGrossSalary(), items, componentMap);
        QualificationLevel qualification = empSalary.getQualificationLevelId() == null
                ? null : qualificationLevels.findById(empSalary.getQualificationLevelId()).orElse(null);
        addEmployeeAllowances(breakdown, empSalary, qualification);
        // ponytail: one scale query per current row; the list already queries per row, batch them if pages grow.
        BigDecimal payScaleBasic = empSalary.getIsCurrent() == 1 && isGraded(jd)
                ? scaleBasic(jd, LocalDate.now()).orElse(null)
                : null;
        BigDecimal loanInstalments = empSalary.getIsCurrent() == 1
                ? loans.monthlyInstalments(employee.getId(), LocalDate.now().withDayOfMonth(1))
                : null;

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
                empSalary.getUpdatedAt(),
                payScaleBasic,
                empSalary.getSocialAmount(),
                empSalary.getQualificationLevelId(),
                qualification == null ? null : qualification.getName(),
                qualification == null ? null : qualification.getNameAr(),
                empSalary.getQualificationAmount(),
                loanInstalments == null || loanInstalments.signum() == 0 ? null : loanInstalments);
    }

    private List<SalaryBreakdownItem> buildBreakdown(
            BigDecimal basicSalary,
            BigDecimal grossSalary,
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
                case PERCENTAGE_OF_GROSS -> grossSalary
                        .multiply(item.getPercentage())
                        .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
                case FORMULA             -> BigDecimal.ZERO; // computed at payroll run
            };

            String calcBasis = switch (calcType) {
                case FIXED               -> "Fixed";
                case PERCENTAGE_OF_BASIC -> shownPercent(item.getPercentage()) + "% of Basic";
                case PERCENTAGE_OF_GROSS -> shownPercent(item.getPercentage()) + "% of Gross";
                case FORMULA             -> "Computed at payroll run";
            };

            breakdown.add(new SalaryBreakdownItem(
                    comp.getComponentCode(), comp.getComponentName(),
                    comp.getComponentNameAr(), comp.getComponentType().name(),
                    amount, calcBasis));
        }

        return breakdown;
    }

    /** Social and Qualification, right after Basic, with the amounts the salary was given (not today's). */
    private void addEmployeeAllowances(List<SalaryBreakdownItem> breakdown, EmployeeSalary empSalary,
                                       QualificationLevel qualification) {
        int at = 1; // after Basic
        if (empSalary.getSocialAmount() != null) {
            SalaryComponent social = componentRepo.findByComponentCode(SOCIAL_ALLOWANCE).orElse(null);
            breakdown.add(at++, new SalaryBreakdownItem(SOCIAL_ALLOWANCE,
                    social != null ? social.getComponentName() : "Social Allowance",
                    social != null ? social.getComponentNameAr() : "بدل اجتماعي",
                    "EARNING", empSalary.getSocialAmount(), "Fixed"));
        }
        if (empSalary.getQualificationAmount() != null) {
            SalaryComponent component = componentRepo.findByComponentCode(QUALIFICATION_ALLOWANCE).orElse(null);
            breakdown.add(at, new SalaryBreakdownItem(QUALIFICATION_ALLOWANCE,
                    component != null ? component.getComponentName() : "Qualification Allowance",
                    component != null ? component.getComponentNameAr() : "بدل مؤهل",
                    "EARNING", empSalary.getQualificationAmount(),
                    qualification != null ? qualification.getName() : "Qualification level"));
        }
    }

    /** A rate as people read it: at most 4 decimals, no trailing zeros (3.3333333333 -> "3.3333", 20.0000000000 -> "20"). */
    static String shownPercent(BigDecimal percentage) {
        return percentage.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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
