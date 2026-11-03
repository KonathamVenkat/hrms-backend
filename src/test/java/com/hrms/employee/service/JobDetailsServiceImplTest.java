package com.hrms.employee.service;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.JobDetailsRequest;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.repository.*;
import com.hrms.employee.service.impl.JobDetailsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobDetailsServiceImplTest {

    @Mock EmployeeJobDetailsRepository jobDetailsRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock DesignationRepository designationRepository;
    @Mock com.hrms.employee.repository.ProfessionRepository professionRepository;
    @Mock OfficeLocationRepository locationRepository;
    @Mock WorkShiftRepository shiftRepository;
    @Mock com.hrms.employee.service.DepartmentHeadService departmentHeads;
    @Mock com.hrms.employee.service.DepartmentSecretaryService departmentSecretaries;
    @Mock com.hrms.employee.repository.CompanyBranchRepository branchRepository;
    @Mock SectionRepository sectionRepository;
    @Mock SpecializationRepository specializationRepository;
    @Mock ContractTypeRepository contractTypeRepository;
    @Mock org.springframework.context.ApplicationEventPublisher events;

    JobDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobDetailsServiceImpl(jobDetailsRepository, employeeRepository,
            departmentRepository, designationRepository, professionRepository, locationRepository, shiftRepository, departmentHeads, departmentSecretaries, branchRepository, sectionRepository, specializationRepository, contractTypeRepository, events);

        // Happy-path references: active employee 5, department 10, designation 20 in 10, location 30.
        when(employeeRepository.findByIdAndIsActive(5L, true)).thenReturn(Optional.of(mock(Employee.class)));
        Department dept = mock(Department.class);
        when(dept.getIsActive()).thenReturn(true);
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(dept));
        Designation desig = mock(Designation.class);
        when(desig.getIsActive()).thenReturn(true);
        when(desig.getDepartmentId()).thenReturn(10L);
        when(designationRepository.findById(20L)).thenReturn(Optional.of(desig));
        OfficeLocation loc = mock(OfficeLocation.class);
        when(loc.getIsActive()).thenReturn(1);
        when(locationRepository.findById(30L)).thenReturn(Optional.of(loc));
    }

    private JobDetailsRequest request(LocalDate effectiveFrom) {
        return JobDetailsRequest.builder()
            .departmentId(10L).designationId(20L).locationId(30L)
            .effectiveFrom(effectiveFrom).build();
    }

    @Test
    void deactivatedEmployeeCannotBeAssignedAJob() {
        when(employeeRepository.findByIdAndIsActive(6L, true)).thenReturn(Optional.empty());
        when(employeeRepository.existsById(6L)).thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(6L, request(LocalDate.now())));
        assertEquals("EMP_INACTIVE", ex.getRuleCode());
    }

    @Test
    void unknownEmployeeIsNotFound() {
        when(employeeRepository.findByIdAndIsActive(6L, true)).thenReturn(Optional.empty());
        when(employeeRepository.existsById(6L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class,
            () -> service.assignJob(6L, request(LocalDate.now())));
    }

    @Test
    void designationMustBelongToTheSelectedDepartment() {
        Designation other = mock(Designation.class);
        when(other.getIsActive()).thenReturn(true);
        when(other.getDepartmentId()).thenReturn(99L);
        when(designationRepository.findById(20L)).thenReturn(Optional.of(other));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now())));
        assertEquals("DESIGNATION_DEPARTMENT_MISMATCH", ex.getRuleCode());
    }

    @Test
    void inactiveDepartmentIsRejected() {
        Department inactive = mock(Department.class);
        when(inactive.getIsActive()).thenReturn(false);
        when(departmentRepository.findById(10L)).thenReturn(Optional.of(inactive));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now())));
        assertEquals("DEPARTMENT_INACTIVE", ex.getRuleCode());
    }

    @Test
    void employeeCannotBeTheirOwnReportingManager() {
        JobDetailsRequest req = request(LocalDate.now());
        req.setReportingManagerId(5L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, req));
        assertEquals("MANAGER_IS_SELF", ex.getRuleCode());
    }

    @Test
    void inactiveManagerIsRejected() {
        when(employeeRepository.findByIdAndIsActive(7L, true)).thenReturn(Optional.empty());
        JobDetailsRequest req = request(LocalDate.now());
        req.setFunctionalManagerId(7L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, req));
        assertEquals("MANAGER_INVALID", ex.getRuleCode());
    }

    @Test
    void effectiveDateMoreThanAYearInThePastIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now().minusYears(1).minusDays(1))));
        assertEquals("INVALID_DATE", ex.getRuleCode());
        verify(jobDetailsRepository, never()).closeCurrentRecord(anyLong(), any(), anyString());
    }

    @Test
    void futureDatedAssignmentIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now().plusDays(30))));
        assertEquals("FUTURE_EFFECTIVE_FROM", ex.getRuleCode());
        verify(jobDetailsRepository, never()).closeCurrentRecord(anyLong(), any(), anyString());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void assignmentStartingTomorrowIsRejected() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(LocalDate.now().plusDays(1))));
        assertEquals("FUTURE_EFFECTIVE_FROM", ex.getRuleCode());
    }

    @Test
    void assignmentStartingTodayIsAllowed() {
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(java.util.NoSuchElementException.class,   // reaches the final lookup, i.e. past the date check
            () -> service.assignJob(5L, request(LocalDate.now())));
        verify(jobDetailsRepository).save(any());
        verify(departmentHeads).clearHeadOf(5L, 10L);
        verify(departmentSecretaries).clearSecretaryOf(5L, 10L);
    }

    private void designationProfession(Long professionId) {
        Designation desig = designationRepository.findById(20L).orElseThrow();
        when(desig.getProfessionId()).thenReturn(professionId);
    }

    private void profession(Long id, boolean active) {
        com.hrms.employee.entity.Profession p = mock(com.hrms.employee.entity.Profession.class);
        when(p.getIsActive()).thenReturn(active);
        when(professionRepository.findById(id)).thenReturn(Optional.of(p));
    }

    private EmployeeJobDetails assignAndCapture(JobDetailsRequest req) {
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, req));
        var saved = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void professionDefaultsToTheDesignationsProfession() {
        designationProfession(7L);

        assertEquals(7L, assignAndCapture(request(LocalDate.now())).getProfessionId());
    }

    @Test
    void professionStaysEmptyWhenNeitherTheRequestNorTheDesignationHasOne() {
        designationProfession(null);

        assertNull(assignAndCapture(request(LocalDate.now())).getProfessionId());
    }

    @Test
    void aProfessionIsAcceptedWhenTheDesignationHasNone_orTheSame() {
        designationProfession(null);
        profession(7L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setProfessionId(7L);

        assertEquals(7L, assignAndCapture(req).getProfessionId());
    }

    @Test
    void aProfessionDifferentFromTheDesignationsIsRejected() {
        designationProfession(7L);
        profession(8L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setProfessionId(8L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("PROFESSION_DESIGNATION_MISMATCH", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void anUnknownOrInactiveProfessionIsRejected() {
        designationProfession(null);
        JobDetailsRequest req = request(LocalDate.now());
        req.setProfessionId(9L);
        when(professionRepository.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.assignJob(5L, req));

        profession(9L, false);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("PROFESSION_INACTIVE", ex.getRuleCode());
    }

    @Test
    void changingTheProfessionOfTheCurrentJobNeedsANewAssignment_butOmittingItKeepsIt() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).professionId(7L).workMode("ON_SITE").build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        JobDetailsRequest changed = request(LocalDate.now());
        changed.setWorkMode("ON_SITE");
        changed.setProfessionId(8L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateCurrentJob(5L, changed));
        assertEquals("STRUCTURAL_CHANGE_NOT_ALLOWED", ex.getRuleCode());

        JobDetailsRequest omitted = request(LocalDate.now());
        omitted.setWorkMode("REMOTE");
        assertThrows(java.util.NoSuchElementException.class, () -> service.updateCurrentJob(5L, omitted));
        assertEquals("REMOTE", current.getWorkMode());
    }

    private EmployeeJobDetails currentWithoutProfession() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).workMode("ON_SITE").build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        return current;
    }

    @Test
    void aCurrentJobWithoutAProfessionCanBeGivenOne() {
        EmployeeJobDetails current = currentWithoutProfession();
        designationProfession(null);
        profession(7L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setWorkMode("ON_SITE");
        req.setProfessionId(7L);

        assertThrows(java.util.NoSuchElementException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals(7L, current.getProfessionId());
    }

    @Test
    void aProfessionGivenToACurrentJobMustMatchTheDesignation() {
        EmployeeJobDetails current = currentWithoutProfession();
        designationProfession(7L);
        profession(8L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setWorkMode("ON_SITE");
        req.setProfessionId(8L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals("PROFESSION_DESIGNATION_MISMATCH", ex.getRuleCode());
        assertNull(current.getProfessionId());
    }

    @Test
    void jobChangesLockTheEmployeeRowFirst() {
        JobDetailsRequest req = request(LocalDate.now().plusDays(30));
        assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));

        var order = inOrder(employeeRepository);
        order.verify(employeeRepository).findByIdForUpdate(5L);
        order.verify(employeeRepository).findByIdAndIsActive(5L, true);
    }

    @Test
    void newAssignmentMustStartAfterTheCurrentOne() {
        LocalDate currentFrom = LocalDate.now().minusDays(30);
        EmployeeJobDetails current = mock(EmployeeJobDetails.class);
        when(current.getEffectiveFrom()).thenReturn(currentFrom);
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.assignJob(5L, request(currentFrom)));
        assertEquals("INVALID_EFFECTIVE_FROM", ex.getRuleCode());
        verify(jobDetailsRepository, never()).closeCurrentRecord(anyLong(), any(), anyString());
        verify(jobDetailsRepository, never()).save(any());
    }

// ── Branch (workflow phase 1) ─────────────────────────────

    private com.hrms.employee.entity.CompanyBranch branch(Long id, Long locationId, boolean active) {
        return com.hrms.employee.entity.CompanyBranch.builder().id(id).code("B" + id).name("Branch " + id)
            .locationId(locationId).isActive(active).build();
    }

    private EmployeeJobDetails savedJobFor(JobDetailsRequest req) {
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.empty());
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        // The save succeeds; the final re-read is not stubbed, so the call ends in NoSuchElementException, as above.
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, req));
        var captor = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void branchOfTheSameLocationIsStoredOnTheNewJobRow() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 30L, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        assertEquals(40L, savedJobFor(req).getCompanyBranchId());
    }

    @Test
    void newAssignmentWithoutABranch_keepsTheCurrentBranchWhenTheLocationIsUnchanged() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).companyBranchId(40L).effectiveFrom(LocalDate.now().minusDays(30)).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, request(LocalDate.now())));
        var captor = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(captor.capture());

        assertEquals(40L, captor.getValue().getCompanyBranchId());
    }

    @Test
    void newAssignmentInAnotherLocation_doesNotCarryTheOldBranchOver() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(31L).companyBranchId(40L).effectiveFrom(LocalDate.now().minusDays(30)).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, request(LocalDate.now())));
        var captor = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(captor.capture());

        assertNull(captor.getValue().getCompanyBranchId());
    }

    /** The branch decides who the branch manager of the employee's waiting requests is. */
    @Test
    void aNewAssignmentThatChangesTheBranchPublishesOrganisationChanged() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(31L).companyBranchId(40L).effectiveFrom(LocalDate.now().minusDays(30)).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(jobDetailsRepository.findCurrentJobByEmployee(5L)).thenReturn(Optional.of(mock(com.hrms.employee.repository.projection.JobDetailsProjection.class)));

        service.assignJob(5L, request(LocalDate.now()));

        verify(events).publishEvent(any(OrganisationChanged.class));
    }

    @Test
    void aNewAssignmentThatKeepsTheBranchPublishesNothing() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).companyBranchId(40L).effectiveFrom(LocalDate.now().minusDays(30)).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        when(jobDetailsRepository.findCurrentJobByEmployee(5L)).thenReturn(Optional.of(mock(com.hrms.employee.repository.projection.JobDetailsProjection.class)));

        service.assignJob(5L, request(LocalDate.now()));

        verify(events, never()).publishEvent(any());
    }

    @Test
    void aFirstAssignmentWithABranchPublishesOrganisationChanged() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 30L, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        savedJobFor(req);

        verify(events).publishEvent(any(OrganisationChanged.class));
    }

    @Test
    void noBranchStoresNull() {
        assertNull(savedJobFor(request(LocalDate.now())).getCompanyBranchId());
        verifyNoInteractions(branchRepository);
    }

    @Test
    void branchOfAnotherLocationIsRejected() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 31L, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("BRANCH_LOCATION_MISMATCH", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void branchWithoutALocationIsRejectedAsMismatch() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, null, true)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("BRANCH_LOCATION_MISMATCH", ex.getRuleCode());
    }

    @Test
    void inactiveBranchIsRejected() {
        when(branchRepository.findById(40L)).thenReturn(Optional.of(branch(40L, 30L, false)));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(40L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("BRANCH_INACTIVE", ex.getRuleCode());
    }

    @Test
    void unknownBranchIsNotFound() {
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(404L);

        assertThrows(ResourceNotFoundException.class, () -> service.assignJob(5L, req));
    }

    @Test
    void changingTheBranchOnAnUpdateIsAStructuralChange() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).companyBranchId(40L).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        JobDetailsRequest req = request(LocalDate.now());
        req.setCompanyBranchId(41L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals("STRUCTURAL_CHANGE_NOT_ALLOWED", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }

    // ── Section ───────────────────────────────────────────────

    private void section(Long id, Long departmentId, boolean active) {
        com.hrms.employee.entity.Section s = com.hrms.employee.entity.Section.builder()
            .id(id).departmentId(departmentId).isActive(active).build();
        when(sectionRepository.findById(id)).thenReturn(Optional.of(s));
    }

    @Test
    void aSectionOfTheChosenDepartmentIsSaved_andNoSectionIsAllowed() {
        section(50L, 10L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setSectionId(50L);

        assertEquals(50L, savedJobFor(req).getSectionId());
    }

    @Test
    void noSectionStoresNull() {
        assertNull(savedJobFor(request(LocalDate.now())).getSectionId());
        verifyNoInteractions(sectionRepository);
    }

    @Test
    void aSectionOfAnotherDepartmentIsRejected() {
        section(50L, 99L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setSectionId(50L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("SECTION_DEPARTMENT_MISMATCH", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void anUnknownOrInactiveSectionIsRejected() {
        JobDetailsRequest req = request(LocalDate.now());
        req.setSectionId(51L);
        when(sectionRepository.findById(51L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.assignJob(5L, req));

        section(51L, 10L, false);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("SECTION_INACTIVE", ex.getRuleCode());
    }

    @Test
    void aCurrentJobWithoutASectionMayBeGivenOne_butASetSectionNeedsANewAssignment() {
        section(50L, 10L, true);
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).workMode("ON_SITE").build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        JobDetailsRequest req = request(LocalDate.now());
        req.setWorkMode("ON_SITE");
        req.setSectionId(50L);

        assertThrows(java.util.NoSuchElementException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals(50L, current.getSectionId());

        req.setSectionId(null);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals("STRUCTURAL_CHANGE_NOT_ALLOWED", ex.getRuleCode());
    }

    // ---- appointment fields: specialization, contract, request numbers, work address (V17)

    private void specialization(Long id, boolean active) {
        com.hrms.employee.entity.Specialization sp = mock(com.hrms.employee.entity.Specialization.class);
        when(sp.getIsActive()).thenReturn(active);
        when(specializationRepository.findById(id)).thenReturn(Optional.of(sp));
    }

    private void contractType(Long id, boolean active) {
        com.hrms.employee.entity.ContractType ct = mock(com.hrms.employee.entity.ContractType.class);
        when(ct.getIsActive()).thenReturn(active);
        when(contractTypeRepository.findById(id)).thenReturn(Optional.of(ct));
    }

    @Test
    void aNewAssignmentStoresTheAppointmentFieldsTrimmed() {
        specialization(3L, true);
        contractType(4L, true);
        JobDetailsRequest req = request(LocalDate.now());
        req.setSpecializationId(3L);
        req.setContractTypeId(4L);
        req.setContractStartDate(LocalDate.of(2026, 1, 1));
        req.setContractEndDate(LocalDate.of(2026, 12, 31));
        req.setFormRequestNo(" F-1 ");
        req.setAppointmentRequestNo("A-2");
        req.setAfe("AFE9");
        req.setCompanyIdCardNo("C77");
        req.setCurrentWorkAddress("  Block 3, Khartoum ");
        req.setCurrentWorkPhone("+249 123");

        EmployeeJobDetails saved = assignAndCapture(req);

        assertEquals(3L, saved.getSpecializationId());
        assertEquals(4L, saved.getContractTypeId());
        assertEquals(LocalDate.of(2026, 12, 31), saved.getContractEndDate());
        assertEquals("F-1", saved.getFormRequestNo());
        assertEquals("A-2", saved.getAppointmentRequestNo());
        assertEquals("AFE9", saved.getAfe());
        assertEquals("C77", saved.getCompanyIdCardNo());
        assertEquals("Block 3, Khartoum", saved.getCurrentWorkAddress());
        assertEquals("+249 123", saved.getCurrentWorkPhone());
    }

    @Test
    void anUnknownOrInactiveSpecializationIsRejected() {
        JobDetailsRequest unknown = request(LocalDate.now());
        unknown.setSpecializationId(99L);
        when(specializationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.assignJob(5L, unknown));

        specialization(3L, false);
        JobDetailsRequest inactive = request(LocalDate.now());
        inactive.setSpecializationId(3L);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, inactive));
        assertEquals("SPECIALIZATION_INACTIVE", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }

    @Test
    void anInactiveContractTypeIsRejected() {
        contractType(4L, false);
        JobDetailsRequest req = request(LocalDate.now());
        req.setContractTypeId(4L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, req));
        assertEquals("CONTRACT_TYPE_INACTIVE", ex.getRuleCode());
    }

    @Test
    void aContractEndingBeforeItStartsIsRejected_butTheSameDayIsFine() {
        JobDetailsRequest backwards = request(LocalDate.now());
        backwards.setContractStartDate(LocalDate.of(2026, 5, 2));
        backwards.setContractEndDate(LocalDate.of(2026, 5, 1));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.assignJob(5L, backwards));
        assertEquals("INVALID_CONTRACT_DATES", ex.getRuleCode());

        JobDetailsRequest sameDay = request(LocalDate.now());
        sameDay.setContractStartDate(LocalDate.of(2026, 5, 1));
        sameDay.setContractEndDate(LocalDate.of(2026, 5, 1));
        assertEquals(LocalDate.of(2026, 5, 1), assignAndCapture(sameDay).getContractEndDate());
    }

    @Test
    void aNewAssignmentKeepsTheAppointmentFieldsItWasNotGiven() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).workMode("ON_SITE").effectiveFrom(LocalDate.now().minusDays(5))
            .afe("OLD-AFE").specializationId(3L).contractTypeId(4L).currentWorkAddress("Old address").build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        when(jobDetailsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        JobDetailsRequest req = request(LocalDate.now());
        req.setAfe("NEW-AFE");

        assertThrows(java.util.NoSuchElementException.class, () -> service.assignJob(5L, req));

        var saved = org.mockito.ArgumentCaptor.forClass(EmployeeJobDetails.class);
        verify(jobDetailsRepository).save(saved.capture());
        assertEquals("NEW-AFE", saved.getValue().getAfe());
        assertEquals(3L, saved.getValue().getSpecializationId());
        assertEquals(4L, saved.getValue().getContractTypeId());
        assertEquals("Old address", saved.getValue().getCurrentWorkAddress());
    }

    @Test
    void updatingTheCurrentJobSetsTheAppointmentFields_andAnEmptyValueClearsOne() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).workMode("ON_SITE").afe("OLD").formRequestNo("F1").specializationId(3L).build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        JobDetailsRequest req = request(LocalDate.now());
        req.setWorkMode("ON_SITE");
        req.setAfe(" NEW ");
        req.setFormRequestNo("   ");

        assertThrows(java.util.NoSuchElementException.class, () -> service.updateCurrentJob(5L, req));

        assertEquals("NEW", current.getAfe());
        assertNull(current.getFormRequestNo());
        assertNull(current.getSpecializationId());
    }

    @Test
    void updatingTheCurrentJobRejectsAnInactiveSpecialization_andSavesNothing() {
        EmployeeJobDetails current = EmployeeJobDetails.builder().employeeId(5L).departmentId(10L).designationId(20L)
            .locationId(30L).workMode("ON_SITE").build();
        when(jobDetailsRepository.findByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(Optional.of(current));
        specialization(3L, false);
        JobDetailsRequest req = request(LocalDate.now());
        req.setWorkMode("ON_SITE");
        req.setSpecializationId(3L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateCurrentJob(5L, req));
        assertEquals("SPECIALIZATION_INACTIVE", ex.getRuleCode());
        verify(jobDetailsRepository, never()).save(any());
    }
}
