package com.hrms.employee.service;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.UserRole;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.CreateEmployeeRequest;
import com.hrms.employee.dto.request.UpdateEmployeeRequest;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.mapper.EmployeeMapper;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.EmployeeLoginAccounts;
import com.hrms.employee.service.impl.EmployeeServiceImpl;
import com.hrms.employee.service.impl.WorkEmailGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Locks in the access-control and lifecycle rules of {@link EmployeeServiceImpl}:
 * who may mint elevated roles, how exits are recorded, and what a deactivated employee can do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmployeeServiceImplTest {

    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeMapper employeeMapper;
    @Mock AuthUserRepository authUserRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmployeeAccessGuard accessGuard;
    @Mock com.hrms.employee.repository.EmployeeJobDetailsRepository jobDetails;

    EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeServiceImpl(
            employeeRepository, employeeMapper, accessGuard,
            new WorkEmailGenerator(employeeRepository, authUserRepository, "nilepet.com"),
            new EmployeeLoginAccounts(authUserRepository, passwordEncoder),
            jobDetails);
    }

    @Test
    void searchEscapesLikeWildcardsAndIgnoresRequestedSort() {
        when(employeeRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());
        var req = com.hrms.employee.dto.request.EmployeeFilterRequest.builder()
            .keyword("50%_a\\b").sortBy("bogus; DROP").sortDir("desc").page(0).size(500).build();

        service.getEmployees(req);

        var keyword  = org.mockito.ArgumentCaptor.forClass(String.class);
        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(employeeRepository).findAllWithFilters(keyword.capture(), any(), any(), any(), any(), any(), pageable.capture());
        assertEquals("50\\%\\_a\\\\b", keyword.getValue());
        assertTrue(pageable.getValue().getSort().isUnsorted());
        assertEquals(100, pageable.getValue().getPageSize());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private CreateEmployeeRequest createRequest(String role, String password) {
        return CreateEmployeeRequest.builder()
            .firstName("Sara").firstNameAr("سارة").lastName("Khan").lastNameAr("خان")
            .personalEmail("sara@example.com").workEmail("sara.khan@nilepet.com")
            .username("sara.khan").password(password).role(role)
            .employmentStatus(EmploymentStatus.PROBATION)
            .build();
    }

    private Employee employee(long id, EmploymentStatus status, boolean active) {
        return Employee.builder()
            .id(id).firstName("A").lastName("B").personalEmail("a@example.com")
            .employmentStatus(status).isActive(active).build();
    }

    private UpdateEmployeeRequest updateRequest() {
        return UpdateEmployeeRequest.builder()
            .firstName("A").firstNameAr("ا").lastName("B").lastNameAr("ب")
            .personalEmail("a@example.com")
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();
    }

    // ── Create ──────────────────────────────────────────────

    @Test
    void hrManagerCannotCreateAnElevatedAccount() {
        loginAs("manager", "HR_MANAGER");

        assertThrows(AccessDeniedException.class,
            () -> service.createEmployee(createRequest("HR_ADMIN", "Str0ngPass")));

        verify(employeeRepository, never()).saveAndFlush(any());
        verify(authUserRepository, never()).save(any());
    }

    @Test
    void hrManagerCannotCreateAPeerManagerAccountEither() {
        loginAs("manager", "HR_MANAGER");
        assertThrows(AccessDeniedException.class,
            () -> service.createEmployee(createRequest("HR_MANAGER", "Str0ngPass")));
    }

    @Test
    void unknownRoleIsRejected() {
        loginAs("admin", "HR_ADMIN");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.createEmployee(createRequest("SUPERUSER", "Str0ngPass")));
        assertEquals("INVALID_ROLE", ex.getRuleCode());
    }

    @Test
    void weakPasswordIsRejectedBeforeAnythingIsWritten() {
        loginAs("manager", "HR_MANAGER");
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.createEmployee(createRequest("EMPLOYEE", "alllowercase")));
        assertEquals("PASSWORD_POLICY", ex.getRuleCode());
        verify(employeeRepository, never()).saveAndFlush(any());
    }

    @Test
    void usernameUniquenessIgnoresCase() {
        loginAs("manager", "HR_MANAGER");
        when(authUserRepository.existsByUsernameIgnoreCase("sara.khan")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
            () -> service.createEmployee(createRequest("EMPLOYEE", "Str0ngPass")));
        verify(employeeRepository, never()).saveAndFlush(any());
    }

    @Test
    void newEmployeeCannotStartAsTerminated() {
        loginAs("manager", "HR_MANAGER");
        CreateEmployeeRequest req = createRequest("EMPLOYEE", "Str0ngPass");
        req.setEmploymentStatus(EmploymentStatus.TERMINATED);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.createEmployee(req));
        assertEquals("INVALID_INITIAL_STATUS", ex.getRuleCode());
    }

    // ── Update ──────────────────────────────────────────────

    @Test
    void deactivatedEmployeeCannotBeEdited() {
        when(employeeRepository.findById(5L))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.TERMINATED, false)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.updateEmployee(5L, updateRequest()));
        assertEquals("EMP_INACTIVE", ex.getRuleCode());
    }

    @Test
    void exitStatusCannotBeSetThroughAPlainUpdate() {
        when(employeeRepository.findById(5L))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.ACTIVE, true)));
        UpdateEmployeeRequest req = updateRequest();
        req.setEmploymentStatus(EmploymentStatus.TERMINATED);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.updateEmployee(5L, req));
        assertEquals("USE_DEACTIVATE_FOR_EXIT", ex.getRuleCode());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void exitedEmployeeCannotBeMovedBackToActiveByUpdate() {
        when(employeeRepository.findById(5L))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.RESIGNED, true)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.updateEmployee(5L, updateRequest()));
        assertEquals("INVALID_STATUS_TRANSITION", ex.getRuleCode());
    }


    @Test
    void anEditFromAStaleScreenIsRefused() {
        loginAs("admin", "HR_ADMIN");
        Employee e = employee(5, EmploymentStatus.ACTIVE, true);
        e.setVersion(3L);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(e));
        UpdateEmployeeRequest req = updateRequest();
        req.setVersion(2L);              // the screen was loaded before someone else saved

        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
            () -> service.updateEmployee(5L, req));
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void anHrManagerCannotEditAnAdministratorsRecord() {
        loginAs("manager", "HR_MANAGER");
        when(employeeRepository.findById(5L))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.ACTIVE, true)));
        when(authUserRepository.findByEmployeeId(5L)).thenReturn(Optional.of(
            AuthUser.builder().employeeId(5L).role(UserRole.HR_ADMIN).build()));

        assertThrows(AccessDeniedException.class, () -> service.updateEmployee(5L, updateRequest()));
        verify(employeeRepository, never()).save(any());
    }
    @Test
    void hrManagerCannotChangeARole() {
        loginAs("manager", "HR_MANAGER");
        Employee e = employee(5, EmploymentStatus.ACTIVE, true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        UpdateEmployeeRequest req = updateRequest();
        req.setRole("HR_ADMIN");

        assertThrows(AccessDeniedException.class, () -> service.updateEmployee(5L, req));
        verify(authUserRepository, never()).save(any());
    }

    @Test
    void hrAdminCannotRemoveTheirOwnAdminRole() {
        loginAs("admin", "HR_ADMIN");
        Employee e = employee(5, EmploymentStatus.ACTIVE, true);
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(accessGuard.currentEmployeeId()).thenReturn(5L);
        when(authUserRepository.findByEmployeeId(5L)).thenReturn(Optional.of(
            AuthUser.builder().employeeId(5L).role(UserRole.HR_ADMIN).build()));
        UpdateEmployeeRequest req = updateRequest();
        req.setRole("EMPLOYEE");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.updateEmployee(5L, req));
        assertEquals("CANNOT_DEMOTE_SELF", ex.getRuleCode());
    }

    // ── Deactivate ──────────────────────────────────────────


    @Test
    void cannotDeactivateAManagerWhoStillHasActiveReports() {
        loginAs("admin", "HR_ADMIN");
        when(employeeRepository.findByIdAndIsActive(5L, true))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.ACTIVE, true)));
        when(accessGuard.currentEmployeeId()).thenReturn(1L);
        when(jobDetails.countActiveReportsOf(5L)).thenReturn(3L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.deactivateEmployee(5L, EmploymentStatus.RESIGNED));
        assertEquals("HAS_ACTIVE_REPORTS", ex.getRuleCode());
        verify(employeeRepository, never()).softDeleteById(anyLong(), anyString());
    }
    @Test
    void cannotDeactivateYourOwnAccount() {
        loginAs("admin", "HR_ADMIN");
        when(employeeRepository.findByIdAndIsActive(5L, true))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.ACTIVE, true)));
        when(accessGuard.currentEmployeeId()).thenReturn(5L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.deactivateEmployee(5L, EmploymentStatus.TERMINATED));
        assertEquals("CANNOT_DEACTIVATE_SELF", ex.getRuleCode());
        verify(employeeRepository, never()).softDeleteById(anyLong(), anyString());
    }

    @Test
    void exitStatusMustBeANonEmployedStatus() {
        loginAs("admin", "HR_ADMIN");
        when(employeeRepository.findByIdAndIsActive(5L, true))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.ACTIVE, true)));
        when(accessGuard.currentEmployeeId()).thenReturn(1L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.deactivateEmployee(5L, EmploymentStatus.ACTIVE));
        assertEquals("INVALID_EXIT_STATUS", ex.getRuleCode());
    }

    @Test
    void deactivationRecordsTheExitStatusAndRevokesTheLogin() {
        loginAs("admin", "HR_ADMIN");
        Employee e = employee(5, EmploymentStatus.ACTIVE, true);
        AuthUser login = AuthUser.builder().employeeId(5L).isActive(true).build();
        when(employeeRepository.findByIdAndIsActive(5L, true)).thenReturn(Optional.of(e));
        when(accessGuard.currentEmployeeId()).thenReturn(1L);
        when(employeeRepository.softDeleteById(5L, "admin")).thenReturn(1);
        when(authUserRepository.findByEmployeeId(5L)).thenReturn(Optional.of(login));

        service.deactivateEmployee(5L, EmploymentStatus.RESIGNED);

        assertEquals(EmploymentStatus.RESIGNED, e.getEmploymentStatus());
        assertFalse(login.getIsActive());
        verify(authUserRepository).save(login);
    }

    @Test
    void deactivatingAnUnknownOrAlreadyInactiveEmployeeIsNotFound() {
        loginAs("admin", "HR_ADMIN");
        when(employeeRepository.findByIdAndIsActive(5L, true)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> service.deactivateEmployee(5L, null));
    }

    // ── Reactivate ──────────────────────────────────────────

    @Test
    void activeEmployeeCannotBeReactivated() {
        when(employeeRepository.findById(5L))
            .thenReturn(Optional.of(employee(5, EmploymentStatus.ACTIVE, true)));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
            () -> service.reactivateEmployee(5L));
        assertEquals("EMP_ALREADY_ACTIVE", ex.getRuleCode());
    }

    @Test
    void rehireRestoresActiveStatusAndTheLogin() {
        loginAs("admin", "HR_ADMIN");
        Employee e = employee(5, EmploymentStatus.TERMINATED, false);
        AuthUser login = AuthUser.builder().employeeId(5L).isActive(false).build();
        when(employeeRepository.findById(5L)).thenReturn(Optional.of(e));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(authUserRepository.findByEmployeeId(5L)).thenReturn(Optional.of(login));

        service.reactivateEmployee(5L);

        assertTrue(e.getIsActive());
        assertEquals(EmploymentStatus.ACTIVE, e.getEmploymentStatus());
        assertTrue(login.getIsActive());
    }
}
