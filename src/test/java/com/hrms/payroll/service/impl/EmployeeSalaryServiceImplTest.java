package com.hrms.payroll.service.impl;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.GradeResponse;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.service.GradeService;
import com.hrms.payroll.dto.response.EmployeeSalaryResponse;
import com.hrms.payroll.dto.response.PayScaleBasisResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.payroll.dto.request.EmployeeSalaryRequest;
import com.hrms.payroll.entity.EmployeeSalary;
import com.hrms.payroll.entity.QualificationLevel;
import com.hrms.payroll.entity.SalaryComponent;
import com.hrms.payroll.entity.SalaryStructure;
import com.hrms.payroll.entity.SalaryStructureItem;
import com.hrms.payroll.enums.CalculationType;
import com.hrms.payroll.enums.ComponentType;
import com.hrms.payroll.repository.EmployeeSalaryRepository;
import com.hrms.payroll.repository.QualificationLevelRepository;
import com.hrms.payroll.repository.SalaryComponentRepository;
import com.hrms.payroll.repository.SalaryStructureItemRepository;
import com.hrms.payroll.repository.SalaryStructureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Assigning a salary: serialised per employee, and the record names the person who made it. */
class EmployeeSalaryServiceImplTest {

    EmployeeSalaryRepository      salaries    = mock(EmployeeSalaryRepository.class);
    SalaryStructureRepository     structures  = mock(SalaryStructureRepository.class);
    SalaryStructureItemRepository items       = mock(SalaryStructureItemRepository.class);
    SalaryComponentRepository     components  = mock(SalaryComponentRepository.class);
    EmployeeRepository            employees   = mock(EmployeeRepository.class);
    EmployeeJobDetailsRepository  jobs        = mock(EmployeeJobDetailsRepository.class);
    DepartmentRepository          departments = mock(DepartmentRepository.class);
    DesignationRepository         designations = mock(DesignationRepository.class);
    EmployeeAccessGuard           guard       = mock(EmployeeAccessGuard.class);
    AuditTrail                    audit       = mock(AuditTrail.class);
    GradeService                  grades      = mock(GradeService.class);
    QualificationLevelRepository  qualificationLevels = mock(QualificationLevelRepository.class);
    EmployeeSalaryServiceImpl     service;

    @BeforeEach
    void setUp() {
        service = new EmployeeSalaryServiceImpl(salaries, structures, items, components, employees, jobs,
                departments, designations, guard, grades, qualificationLevels, audit);
        Employee employee = new Employee();
        employee.setId(5L);
        employee.setEmployeeCode("EMP-5");
        employee.setFirstName("Sara");
        employee.setLastName("Khan");
        when(employees.findByIdForUpdate(5L)).thenReturn(Optional.of(employee));
        SalaryStructure structure = mock(SalaryStructure.class);
        when(structure.getIsActive()).thenReturn(1);
        when(structure.getStructureId()).thenReturn(3L);
        when(structure.getStructureName()).thenReturn("Standard");
        when(structures.findByIdForUpdate(3L)).thenReturn(Optional.of(structure));
        when(structures.findById(3L)).thenReturn(Optional.of(structure));
        when(items.findByStructureIdAndIsActiveOrderBySortOrderAsc(3L, 1)).thenReturn(List.of());
        when(salaries.findNextSequenceValue()).thenReturn(900L);
        when(salaries.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private EmployeeSalaryRequest request() {
        return new EmployeeSalaryRequest(5L, 3L, new BigDecimal("1500.00"), LocalDate.now().toString(), null, null, null);
    }

    @Test
    void theEmployeeRowIsLockedBeforeTheCurrentRecordIsClosedAndTheNewOneInserted() {
        EmployeeSalary previous = EmployeeSalary.builder().empSalaryId(1L).employeeId(5L)
                .effectiveFrom(LocalDate.now().minusDays(30)).isCurrent(1).build();
        when(salaries.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(previous));

        service.assign(request());

        InOrder order = inOrder(employees, structures, salaries);
        order.verify(employees).findByIdForUpdate(5L);
        order.verify(structures).findByIdForUpdate(3L);
        order.verify(salaries).deactivateCurrent(5L);
        order.verify(salaries).save(any());
        verify(components).findAllByIdForUpdate(any());
    }

    @Test
    void theRecordIsCreatedByWhoeverAssignedItNotByTheEmployeeItBelongsTo() {
        when(salaries.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());

        service.assign(request());

        ArgumentCaptor<EmployeeSalary> saved = ArgumentCaptor.forClass(EmployeeSalary.class);
        verify(salaries).save(saved.capture());
        assertNotEquals("EMP-5", saved.getValue().getCreatedBy());
        assertEquals(new BigDecimal("1500.00"), saved.getValue().getBasicSalary());
    }

    @Test
    void aSalaryIsReadByItsOwnerOrAnHrAdminAndByNobodyElse() {
        EmployeeSalary current = EmployeeSalary.builder().empSalaryId(1L).employeeId(5L).structureId(3L)
                .basicSalary(new BigDecimal("1500.00")).grossSalary(new BigDecimal("1500.00"))
                .netSalary(new BigDecimal("1500.00")).currency("SSP").effectiveFrom(LocalDate.now()).isCurrent(1).build();
        when(salaries.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        Employee owner = new Employee();
        owner.setId(5L);
        owner.setEmployeeCode("EMP-5");
        owner.setFirstName("Sara");
        owner.setLastName("Khan");
        when(employees.findById(5L)).thenReturn(Optional.of(owner));

        // an HR_MANAGER (or anyone else) who is not the owner and not an HR_ADMIN
        when(guard.isSelfOrAdmin(5L)).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getCurrentSalary(5L));

        // the owner or an HR_ADMIN
        when(guard.isSelfOrAdmin(5L)).thenReturn(true);
        assertEquals(new BigDecimal("1500.00"), service.getCurrentSalary(5L).basicSalary());
    }

    @Test
    void anUnknownEmployeeIsNotFoundAndNothingIsWritten() {
        when(employees.findByIdForUpdate(6L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.assign(
                new EmployeeSalaryRequest(6L, 3L, new BigDecimal("1500.00"), LocalDate.now().toString(), null, null, null)));
        verify(salaries, never()).save(any());
        verify(salaries, never()).deactivateCurrent(any());
    }

    // ── Pay scale (step 3): a graded employee's basic comes from the scale, on the grade's structure ──

    /** In the past, so the scale on the effective date is told apart from today's (the current-salary flag). */
    private static final LocalDate DAY = LocalDate.now().minusDays(10);

    /** Employee 5's current job: grade 8, increment 1; grade 8 is paid on structure 3 and scales 460,462 on DAY. */
    private void graded() {
        EmployeeJobDetails job = EmployeeJobDetails.builder().employeeId(5L).departmentId(1L).designationId(1L)
            .locationId(1L).gradeId(8L).incrementNo(1).build();
        when(jobs.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(job));
        SalaryStructure gradeStructure = structures.findById(3L).orElseThrow();
        when(structures.findByGradeId(8L)).thenReturn(Optional.of(gradeStructure));
        when(grades.findStartingScale(8L, 1, DAY)).thenReturn(Optional.of(new BigDecimal("460462")));
    }

    private EmployeeSalaryRequest request(BigDecimal basic) {
        return new EmployeeSalaryRequest(5L, 3L, basic, DAY.toString(), null, null, null);
    }

    private void assertRefused(String rule, EmployeeSalaryRequest req) {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assign(req));
        assertEquals(rule, ex.getRuleCode());
        verify(salaries, never()).deactivateCurrent(any());
        verify(salaries, never()).save(any());
    }

    @Test
    void withoutAGradeTheBasicMustBeTyped() {
        assertRefused("BASIC_REQUIRED", request(null));
    }

    @Test
    void aGradedEmployeesBasicIsTheScaleAmountOnTheEffectiveDate() {
        graded();
        EmployeeSalaryResponse saved = service.assign(request(null));
        assertEquals(0, new BigDecimal("460462").compareTo(saved.basicSalary()));
        verify(grades).findStartingScale(8L, 1, DAY);
    }

    @Test
    void theScaleAmountMayBeSentAsWell_butAnyOtherAmountIsRefused() {
        graded();
        assertEquals(0, new BigDecimal("460462").compareTo(service.assign(request(new BigDecimal("460462.00"))).basicSalary()));
    }

    @Test
    void aTypedAmountOtherThanTheScaleIsRefusedBeforeAnythingIsWritten() {
        graded();
        assertRefused("BASIC_NOT_FROM_SCALE", request(new BigDecimal("500000")));
    }

    @Test
    void aGradedEmployeeIsPaidOnTheGradesOwnStructure() {
        graded();
        SalaryStructure other = mock(SalaryStructure.class);
        when(other.getStructureId()).thenReturn(4L);
        when(other.getStructureName()).thenReturn("Grade 8");
        when(structures.findByGradeId(8L)).thenReturn(Optional.of(other));
        assertRefused("STRUCTURE_GRADE_MISMATCH", request(null));
    }

    @Test
    void aGradeWithoutALinkedStructureOrWithoutAScaleOnTheDateIsRefused() {
        graded();
        when(structures.findByGradeId(8L)).thenReturn(Optional.empty());
        assertRefused("GRADE_STRUCTURE_MISSING", request(null));
    }

    @Test
    void noScaleAmountOnTheEffectiveDateIsRefused() {
        graded();
        when(grades.findStartingScale(8L, 1, DAY)).thenReturn(Optional.empty());
        assertRefused("PAY_SCALE_MISSING", request(null));
    }

    @Test
    void theBasisTellsTheScreenWhatToFillIn() {
        Employee employee = new Employee();
        employee.setId(5L);
        when(employees.findById(5L)).thenReturn(Optional.of(employee));
        PayScaleBasisResponse none = service.getPayScaleBasis(5L, DAY);
        assertEquals(false, none.graded());
        assertEquals(null, none.basicSalary());

        graded();
        GradeResponse grade = GradeResponse.builder().name("Grade 8").nameAr("الدرجة 8").build();
        when(grades.findGrade(8L)).thenReturn(Optional.of(grade));
        PayScaleBasisResponse basis = service.getPayScaleBasis(5L, DAY);
        assertEquals(true, basis.graded());
        assertEquals("Grade 8", basis.gradeName());
        assertEquals(1, basis.incrementNo());
        assertEquals(3L, basis.structureId());
        assertEquals(0, new BigDecimal("460462").compareTo(basis.basicSalary()));
    }

    @Test
    void theCurrentSalaryCarriesTodaysScaleAmountSoAChangedIncrementShows() {
        graded();
        when(grades.findStartingScale(8L, 1, LocalDate.now())).thenReturn(Optional.of(new BigDecimal("483485")));
        EmployeeSalaryResponse saved = service.assign(request(null));
        // paid 460,462 from the effective date, while today's scale for the same step is 483,485: a new salary is due
        assertEquals(0, new BigDecimal("460462").compareTo(saved.basicSalary()));
        assertEquals(0, new BigDecimal("483485").compareTo(saved.payScaleBasic()));
    }

    // ── Rates with 10 decimals (V31): Armed Forces is 1/30 of gross, as in the salary sheet ──

    private SalaryComponent component(long id, String code, ComponentType type, CalculationType calc) {
        return SalaryComponent.builder().componentId(id).componentCode(code).componentName(code).componentNameAr(code)
            .componentType(type).calcType(calc).build();
    }

    @Test
    void armedForcesAtOneThirtiethOfGrossMatchesTheSalarySheet() {
        // Grade 5, increment 1 from the screenshot: gross 2,298,356.80 (basic 700,306 + 1,598,050.80 of allowances)
        SalaryStructureItem allowances = SalaryStructureItem.builder().structureId(3L).componentId(1L)
            .amount(new BigDecimal("1598050.80")).build();
        SalaryStructureItem insurance = SalaryStructureItem.builder().structureId(3L).componentId(2L)
            .percentage(new BigDecimal("8")).build();
        SalaryStructureItem armedForces = SalaryStructureItem.builder().structureId(3L).componentId(3L)
            .percentage(new BigDecimal("3.3333333333")).build();
        when(items.findByStructureIdAndIsActiveOrderBySortOrderAsc(3L, 1)).thenReturn(List.of(allowances, insurance, armedForces));
        when(components.findAllByIdForUpdate(any())).thenReturn(List.of(
            component(1L, "ALLOWANCES", ComponentType.EARNING, CalculationType.FIXED),
            component(2L, "INSURANCE", ComponentType.STATUTORY, CalculationType.PERCENTAGE_OF_GROSS),
            component(3L, "ARMED_FORCES", ComponentType.STATUTORY, CalculationType.PERCENTAGE_OF_GROSS)));

        EmployeeSalaryResponse saved = service.assign(request(new BigDecimal("700306")));

        assertEquals(0, new BigDecimal("2298356.80").compareTo(saved.grossSalary()));
        // sheet: gross - gross*8% - gross/30 = 2,298,356.80 - 183,868.544 - 76,611.893 (was 76,535.281 at 3.33%)
        assertEquals(0, new BigDecimal("2037876.363").compareTo(saved.netSalary()));
        var armed = saved.breakdown().stream().filter(b -> "ARMED_FORCES".equals(b.componentCode())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("76611.893").compareTo(armed.amount()));
        assertEquals("3.3333% of Gross", armed.calcBasis());
    }

    @Test
    void ratesAreShownWithAtMostFourDecimals() {
        assertEquals("3.3333", EmployeeSalaryServiceImpl.shownPercent(new BigDecimal("3.3333333333")));
        assertEquals("20", EmployeeSalaryServiceImpl.shownPercent(new BigDecimal("20.0000000000")));
        assertEquals("3.33", EmployeeSalaryServiceImpl.shownPercent(new BigDecimal("3.33")));
        assertEquals("0", EmployeeSalaryServiceImpl.shownPercent(BigDecimal.ZERO));
    }

    // ── Per-employee allowances (step 4a): Social and Qualification are earnings of this salary ──

    private EmployeeSalaryRequest withAllowances(Boolean social, Long qualificationLevelId) {
        return new EmployeeSalaryRequest(5L, 3L, new BigDecimal("700306"), DAY.toString(), null, social, qualificationLevelId);
    }

    /** Structure 3 charges Insurance 8% of gross; Social pays 135,000; level 3 pays 202,500. */
    private void allowancesSetUp() {
        SalaryStructureItem insurance = SalaryStructureItem.builder().structureId(3L).componentId(2L)
            .percentage(new BigDecimal("8")).build();
        when(items.findByStructureIdAndIsActiveOrderBySortOrderAsc(3L, 1)).thenReturn(List.of(insurance));
        when(components.findAllByIdForUpdate(any())).thenReturn(List.of(
            component(2L, "INSURANCE", ComponentType.STATUTORY, CalculationType.PERCENTAGE_OF_GROSS)));
        SalaryComponent social = component(14L, "SOCIAL_ALLOW", ComponentType.EARNING, CalculationType.FIXED);
        social.setDefaultValue(new BigDecimal("135000.0000000000"));
        when(components.findByComponentCode("SOCIAL_ALLOW")).thenReturn(Optional.of(social));
        when(qualificationLevels.findById(3L)).thenReturn(Optional.of(QualificationLevel.builder().id(3L).code("QL_202500")
            .name("Qualification 202,500").nameAr("x").monthlyAmount(new BigDecimal("202500.00")).isActive(true).build()));
    }

    @Test
    void socialAndQualificationAreEarningsOfTheSalary_soInsuranceIsChargedOnThem() {
        allowancesSetUp();
        EmployeeSalaryResponse saved = service.assign(withAllowances(true, 3L));

        // gross 700,306 + 135,000 + 202,500 = 1,037,806; Insurance 8% = 83,024.48; net 954,781.52 (sheet: Q = ... + M + N)
        assertEquals(0, new BigDecimal("1037806").compareTo(saved.grossSalary()));
        assertEquals(0, new BigDecimal("954781.52").compareTo(saved.netSalary()));
        assertEquals(0, new BigDecimal("135000").compareTo(saved.socialAmount()));
        assertEquals(3L, saved.qualificationLevelId());
        assertEquals("Qualification 202,500", saved.qualificationLevelName());
        assertEquals(0, new BigDecimal("202500").compareTo(saved.qualificationAmount()));
        assertEquals(List.of("BASIC", "SOCIAL_ALLOW", "QUALIFICATION", "INSURANCE"),
            saved.breakdown().stream().map(EmployeeSalaryResponse.SalaryBreakdownItem::componentCode).toList());

        var captor = ArgumentCaptor.forClass(EmployeeSalary.class);
        verify(salaries).save(captor.capture());
        assertEquals(0, new BigDecimal("135000").compareTo(captor.getValue().getSocialAmount()));
        assertEquals(0, new BigDecimal("202500").compareTo(captor.getValue().getQualificationAmount()));
    }

    @Test
    void notChosen_nothingIsAddedAndNothingIsStored() {
        allowancesSetUp();
        EmployeeSalaryResponse saved = service.assign(withAllowances(null, null));
        assertEquals(0, new BigDecimal("700306").compareTo(saved.grossSalary()));
        assertEquals(null, saved.socialAmount());
        assertEquals(null, saved.qualificationLevelId());
        verify(components, never()).findByComponentCode("SOCIAL_ALLOW");
    }

    @Test
    void anInactiveSocialComponentOrOneWithoutAnAmountIsRefused() {
        allowancesSetUp();
        SalaryComponent social = components.findByComponentCode("SOCIAL_ALLOW").orElseThrow();
        social.setIsActive(0);
        assertRefused("SOCIAL_ALLOWANCE_UNAVAILABLE", withAllowances(true, null));
        social.setIsActive(1);
        social.setDefaultValue(BigDecimal.ZERO);
        assertRefused("SOCIAL_ALLOWANCE_UNAVAILABLE", withAllowances(true, null));
    }

    @Test
    void anInactiveOrUnknownQualificationLevelIsRefused() {
        allowancesSetUp();
        qualificationLevels.findById(3L).orElseThrow().setIsActive(false);
        assertRefused("QUALIFICATION_LEVEL_INACTIVE", withAllowances(null, 3L));
        assertThrows(ResourceNotFoundException.class, () -> service.assign(withAllowances(null, 99L)));
        verify(salaries, never()).save(any());
    }
}
