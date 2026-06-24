package com.hrms.employee.service.impl;

import com.hrms.auth.entity.AuthUser;
import com.hrms.auth.repository.AuthUserRepository;
import com.hrms.common.dto.PagedResponse;
import com.hrms.common.enums.EmploymentStatus;
import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.DuplicateResourceException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.CreateEmployeeRequest;
import com.hrms.employee.dto.request.EmployeeFilterRequest;
import com.hrms.employee.dto.request.UpdateEmployeeRequest;
import com.hrms.employee.dto.response.EmployeeDetailResponse;
import com.hrms.employee.dto.response.EmployeeResponse;
import com.hrms.employee.dto.response.EmployeeSummaryResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.mapper.EmployeeMapper;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.projection.EmployeeDetailProjection;
import com.hrms.employee.repository.projection.EmployeeListProjection;
import com.hrms.employee.repository.specification.EmployeeSpecification;
import com.hrms.employee.service.EmployeeService;
import com.hrms.common.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Primary implementation of {@link EmployeeService}.
 *
 * <h3>Transaction strategy:</h3>
 * <ul>
 *   <li>All read methods are annotated {@code @Transactional(readOnly = true)}.</li>
 *   <li>All write methods use the default read-write transaction.</li>
 * </ul>
 *
 * <h3>workEmail auto-generation:</h3>
 * Since the create form no longer collects workEmail (it is managed separately
 * alongside department/designation in the Job Details tab), the service generates
 * it automatically using the pattern: {@code firstName.lastName@hrms.com}.
 * If that address is already taken a numeric suffix is appended until unique.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository  employeeRepository;
    private final EmployeeMapper      employeeMapper;
    private final AuthUserRepository  authUserRepository;
    private final PasswordEncoder     passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    // ── Allowed sort columns (prevents SQL-injection via arbitrary names) ─────
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "employeeCode", "firstName", "lastName", "workEmail",
        "hireDate", "employmentStatus", "employmentType", "nationality", "createdAt"
    );
    private static final String DEFAULT_SORT_FIELD = "id";

    /**
     * Work email domain used for auto-generation.
     * Move to application.properties / @Value if the domain should be configurable.
     */
    private static final String WORK_EMAIL_DOMAIN = "hrms.com";

    // ──────────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        log.info("Creating new employee: {} {}", request.getFirstName(), request.getLastName());

        // ── 1. Resolve workEmail ───────────────────────────────
        // If the frontend/caller did not supply a workEmail, generate one.
        // Format: firstname.lastname@hrms.com  (with numeric suffix if taken)
        String workEmail = resolveWorkEmail(request);
        request.setWorkEmail(workEmail);   // normalise so later checks use the same value

        // ── 2. Uniqueness checks ───────────────────────────────
        if (employeeRepository.existsByWorkEmailIgnoreCase(workEmail)) {
            throw new DuplicateResourceException("Employee", "workEmail", workEmail);
        }
        if (employeeRepository.existsByPersonalEmailIgnoreCase(request.getPersonalEmail())) {
            throw new DuplicateResourceException("Employee", "personalEmail", request.getPersonalEmail());
        }
        if (authUserRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("AuthUser", "username", request.getUsername());
        }
        if (authUserRepository.existsByEmail(workEmail)) {
            throw new DuplicateResourceException("AuthUser", "email", workEmail);
        }

        // ── 3. Date validations ────────────────────────────────
        validateEmploymentDates(
            request.getDateOfBirth(),
            request.getHireDate(),
            request.getProbationEndDate(),
            request.getConfirmationDate()
        );

        // ── 4. Build and save Employee ─────────────────────────
        // departmentId and designationId are intentionally NOT set here —
        // they are managed from the Job Details tab after creation.
        Employee employee = Employee.builder()
            .firstName(request.getFirstName().trim())
            .firstNameAr(request.getFirstNameAr().trim())
            .middleName(request.getMiddleName())
            .middleNameAr(request.getMiddleNameAr())
            .lastName(request.getLastName().trim())
            .lastNameAr(request.getLastNameAr().trim())
            .dateOfBirth(request.getDateOfBirth())
            .gender(request.getGender())
            .bloodGroup(request.getBloodGroup())
            .maritalStatus(request.getMaritalStatus())
            .nationality(request.getNationality())
            .religion(request.getReligion())
            .personalEmail(request.getPersonalEmail().trim().toLowerCase())
            .workEmail(workEmail)
            .personalPhone(request.getPersonalPhone())
            .workPhone(request.getWorkPhone())
            .hireDate(request.getHireDate())
            .probationEndDate(request.getProbationEndDate())
            .confirmationDate(request.getConfirmationDate())
            .employmentStatus(
                request.getEmploymentStatus() != null
                    ? request.getEmploymentStatus()
                    : EmploymentStatus.PROBATION)
            .employmentType(request.getEmploymentType())
            .isActive(true)
            // departmentId / designationId are null on initial create; set via Job Details tab
            .build();

        Employee savedEmployee = employeeRepository.save(employee);
        log.info("Employee saved. ID: {}, Code: {}, workEmail: {}",
            savedEmployee.getId(), savedEmployee.getEmployeeCode(), workEmail);

        // ── 5. Build full name for AUTH_USERS ──────────────────
        String fullNameEn = buildFullName(
            request.getFirstName().trim(),
            request.getMiddleName(),
            request.getLastName().trim()
        );

        UserRole userRole;
        try {
            userRole = UserRole.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid role: '" + request.getRole() +
                "'. Allowed values: HR_ADMIN, HR_MANAGER, EMPLOYEE");
        }

        // ── 6. Hash password and save AuthUser ─────────────────
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        AuthUser authUser = AuthUser.builder()
            .username(request.getUsername().trim().toLowerCase())
            .passwordHash(hashedPassword)
            .email(workEmail)
            .fullNameEn(fullNameEn)
            .role(userRole)
            .employeeId(savedEmployee.getId())
            .employeeCode(savedEmployee.getEmployeeCode())
            .failedAttempts(0)
            .isActive(true)
            .isLocked(false)
            .createdBy(getCurrentAuditor())
            .createdAt(java.time.LocalDateTime.now())
            .build();

        log.info("Building AuthUser — fullNameEn: '{}', username: '{}', email: '{}'",
            fullNameEn, request.getUsername(), workEmail);

        authUserRepository.save(authUser);
        log.info("AuthUser created for employee. Username: {}", request.getUsername());

        // TODO: publish Kafka event → EmployeeCreatedEvent

        return employeeMapper.toResponse(savedEmployee);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public EmployeeResponse getEmployeeByCode(String employeeCode) {
        Employee employee = employeeRepository
            .findByEmployeeCodeIgnoreCase(employeeCode)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "employeeCode", employeeCode));
        return employeeMapper.toResponse(employee);
    }

    @Override
    public List<EmployeeSummaryResponse> getAllActiveEmployeesForLookup() {
        List<Employee> employees = employeeRepository.findAll(EmployeeSpecification.isActive());
        return employeeMapper.toSummaryResponseList(employees);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public EmployeeDetailResponse updateEmployee(Long id, UpdateEmployeeRequest request) {
        log.info("Updating employee id: {}", id);

        // ── 1. Find existing employee ──────────────────────────
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));

        // ── 2. Personal email uniqueness check (allow same) ────
        if (!employee.getPersonalEmail().equalsIgnoreCase(request.getPersonalEmail())
                && employeeRepository.existsByPersonalEmailIgnoreCaseAndIdNot(
                    request.getPersonalEmail(), id)) {
            throw new DuplicateResourceException(
                "Employee", "personalEmail", request.getPersonalEmail());
        }

        // ── 3. Date validations ────────────────────────────────
        validateEmploymentDates(
            request.getDateOfBirth(),
            request.getHireDate(),
            request.getProbationEndDate(),
            request.getConfirmationDate()
        );

        // ── 4. Update employee fields ──────────────────────────
        // departmentId and designationId are deliberately NOT updated here —
        // they are managed from the Job Details tab (separate endpoint).
        employee.setFirstName(request.getFirstName().trim());
        employee.setFirstNameAr(request.getFirstNameAr().trim());
        employee.setMiddleName(request.getMiddleName());
        employee.setMiddleNameAr(request.getMiddleNameAr());
        employee.setLastName(request.getLastName().trim());
        employee.setLastNameAr(request.getLastNameAr().trim());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.setGender(request.getGender());
        employee.setBloodGroup(request.getBloodGroup());
        employee.setMaritalStatus(request.getMaritalStatus());
        employee.setNationality(request.getNationality());
        employee.setReligion(request.getReligion());
        employee.setProfilePhotoUrl(request.getProfilePhotoUrl());
        employee.setPersonalEmail(request.getPersonalEmail().trim().toLowerCase());
        employee.setPersonalPhone(request.getPersonalPhone());
        employee.setWorkPhone(request.getWorkPhone());
        employee.setHireDate(request.getHireDate());
        employee.setProbationEndDate(request.getProbationEndDate());
        employee.setConfirmationDate(request.getConfirmationDate());
        employee.setEmploymentType(request.getEmploymentType());
        employee.setEmploymentStatus(request.getEmploymentStatus());

        Employee updated = employeeRepository.save(employee);
        log.info("Employee updated. ID: {}", updated.getId());

        // ── 5. Update Auth User role if changed ────────────────
        if (request.getRole() != null && !request.getRole().isBlank()) {
            updateAuthUserRole(updated, request.getRole());
        }

        // ── 6. Update Auth User full name ──────────────────────
        updateAuthUserFullName(updated);

        // ── 7. Return full detail response ─────────────────────
        return getEmployeeById(id);
    }

    @Override
    @Transactional
    public EmployeeResponse updateProfilePhoto(Long id, String photoUrl) {
        Employee employee = findActiveEmployeeById(id);
        employee.setProfilePhotoUrl(photoUrl);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Delete / Deactivation
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deactivateEmployee(Long id) {
        log.info("Deactivating employee ID: {}", id);
        Employee employee = findActiveEmployeeById(id);

        if (!employee.getEmploymentStatus().isCurrentlyEmployed()) {
            log.warn("Deactivating employee ID: {} who has status: {}",
                id, employee.getEmploymentStatus());
        }

        String currentUser = getCurrentAuditor();
        int updated = employeeRepository.softDeleteById(id, currentUser);

        if (updated == 0) {
            throw new ResourceNotFoundException("Employee", "id", id);
        }

        log.info("Employee ID: {} deactivated by: {}", id, currentUser);
        // TODO: publish Kafka event → EmployeeDeactivatedEvent
    }

    @Override
    @Transactional
    public EmployeeResponse reactivateEmployee(Long id) {
        log.info("Reactivating employee ID: {}", id);

        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));

        if (Boolean.TRUE.equals(employee.getIsActive())) {
            throw new BusinessRuleException(
                "EMP_ALREADY_ACTIVE",
                "Employee with ID " + id + " is already active"
            );
        }

        employee.setIsActive(true);
        Employee reactivated = employeeRepository.save(employee);
        log.info("Employee ID: {} reactivated", id);

        // TODO: publish Kafka event → EmployeeReactivatedEvent
        return employeeMapper.toResponse(reactivated);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Existence / validation utilities
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public boolean existsActiveEmployee(Long id) {
        return employeeRepository.findByIdAndIsActive(id, true).isPresent();
    }

    @Override
    public boolean allEmployeesExist(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return false;
        long count = ids.stream().filter(this::existsActiveEmployee).count();
        return count == ids.size();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Paginated search
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public PagedResponse<EmployeeSummaryResponse> getEmployees(EmployeeFilterRequest req) {
        Pageable pageable = buildPageable(req);

        Page<EmployeeListProjection> page = employeeRepository.findAllWithFilters(
            req.getKeyword(),
            req.getDepartmentId(),
            req.getEmploymentStatus() != null ? req.getEmploymentStatus().name() : null,
            req.getEmploymentType()   != null ? req.getEmploymentType().name()   : null,
            req.getGender()           != null ? req.getGender().name()           : null,
            pageable
        );

        return PagedResponse.from(page.map(this::projectionToSummary));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Get by ID (returns full EmployeeDetailResponse)
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public EmployeeDetailResponse getEmployeeById(Long id) {
        log.debug("Fetching employee detail for id: {}", id);

        EmployeeDetailProjection p = employeeRepository.findDetailById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));

        return toDetailResponse(p);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the work email to use for a new employee.
     *
     * <p>If the request already contains a non-blank workEmail (e.g., submitted
     * via API or batch import) that value is used as-is.
     *
     * <p>Otherwise the email is generated from the name:
     * {@code firstname.lastname@hrms.com}.
     * A numeric suffix (1, 2, …) is appended until a unique address is found.
     */
    private String resolveWorkEmail(CreateEmployeeRequest request) {
        // Caller supplied a work email — use it (validation annotation ensures format)
        if (StringUtils.hasText(request.getWorkEmail())) {
            return request.getWorkEmail().trim().toLowerCase();
        }

        // Auto-generate from name
        String base = sanitizeEmailPart(request.getFirstName())
                    + "."
                    + sanitizeEmailPart(request.getLastName())
                    + "@" + WORK_EMAIL_DOMAIN;

        if (!employeeRepository.existsByWorkEmailIgnoreCase(base)
                && !authUserRepository.existsByEmail(base)) {
            return base;
        }

        // Append numeric suffix until unique
        int counter = 1;
        while (counter <= 999) {
            String candidate = sanitizeEmailPart(request.getFirstName())
                             + "."
                             + sanitizeEmailPart(request.getLastName())
                             + counter
                             + "@" + WORK_EMAIL_DOMAIN;
            if (!employeeRepository.existsByWorkEmailIgnoreCase(candidate)
                    && !authUserRepository.existsByEmail(candidate)) {
                return candidate;
            }
            counter++;
        }

        // Extremely unlikely fallback — use username@domain
        return request.getUsername().toLowerCase() + "@" + WORK_EMAIL_DOMAIN;
    }

    /** Lowercases and strips characters that are not valid in an email local-part. */
    private String sanitizeEmailPart(String value) {
        return value.trim()
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]", "");
    }

    private Employee findActiveEmployeeById(Long id) {
        return employeeRepository
            .findByIdAndIsActive(id, true)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
    }

    private void updateAuthUserRole(Employee employee, String roleStr) {
        try {
            UserRole newRole = UserRole.valueOf(roleStr.toUpperCase());
            authUserRepository.findByEmployeeId(employee.getId())
                .ifPresent(authUser -> {
                    if (!authUser.getRole().equals(newRole)) {
                        authUser.setRole(newRole);
                        authUser.setUpdatedAt(java.time.LocalDateTime.now());
                        authUserRepository.save(authUser);
                        log.info("Updated auth user role to {} for employee {}",
                            newRole, employee.getId());
                    }
                });
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role value during update: {}", roleStr);
        }
    }

    private void updateAuthUserFullName(Employee employee) {
        String fullNameEn = buildFullName(
            employee.getFirstName(),
            employee.getMiddleName(),
            employee.getLastName());

        authUserRepository.findByEmployeeId(employee.getId())
            .ifPresent(authUser -> {
                authUser.setFullNameEn(fullNameEn);
                authUser.setUpdatedAt(java.time.LocalDateTime.now());
                authUserRepository.save(authUser);
            });
    }

    private void validateEmploymentDates(
            java.time.LocalDate dob,
            java.time.LocalDate hireDate,
            java.time.LocalDate probationEnd,
            java.time.LocalDate confirmationDate) {

        if (dob != null && hireDate != null && hireDate.isBefore(dob)) {
            throw new IllegalArgumentException("Hire date cannot be before date of birth.");
        }
        if (hireDate != null && probationEnd != null && probationEnd.isBefore(hireDate)) {
            throw new IllegalArgumentException("Probation end date must be after hire date.");
        }
        if (probationEnd != null && confirmationDate != null
                && confirmationDate.isBefore(probationEnd)) {
            throw new IllegalArgumentException("Confirmation date must be after probation end date.");
        }
    }

    private EmployeeSummaryResponse projectionToSummary(EmployeeListProjection p) {
        StringBuilder fullName = new StringBuilder();
        if (p.getFirstName()  != null) fullName.append(p.getFirstName().trim());
        if (p.getMiddleName() != null && !p.getMiddleName().isBlank())
            fullName.append(" ").append(p.getMiddleName().trim());
        if (p.getLastName()   != null) fullName.append(" ").append(p.getLastName().trim());

        return EmployeeSummaryResponse.builder()
            .employeeId(p.getEmployeeId())
            .employeeCode(p.getEmployeeCode())
            .fullNameEn(fullName.toString().trim())
            .firstNameAr(p.getFirstNameAr())
            .lastNameAr(p.getLastNameAr())
            .gender(p.getGender())
            .workEmail(p.getWorkEmail())
            .workPhone(p.getWorkPhone())
            .profilePhotoUrl(p.getProfilePhotoUrl())
            .employmentStatus(p.getEmploymentStatus())
            .employmentType(p.getEmploymentType())
            .nationality(p.getNationality())
            .hireDate(p.getHireDate())
            .isActive(p.getIsActive() != null && p.getIsActive() == 1)
            .departmentId(p.getDepartmentId())
            .departmentName(p.getDepartmentName())
            .departmentCode(p.getDepartmentCode())
            .designationId(p.getDesignationId())
            .designationTitle(p.getDesignationTitle())
            .gradeLevel(p.getGradeLevel())
            .build();
    }

    private EmployeeDetailResponse toDetailResponse(EmployeeDetailProjection p) {
        StringBuilder fullNameEn = new StringBuilder();
        if (p.getFirstName()  != null) fullNameEn.append(p.getFirstName().trim());
        if (p.getMiddleName() != null && !p.getMiddleName().isBlank())
            fullNameEn.append(" ").append(p.getMiddleName().trim());
        if (p.getLastName()   != null) fullNameEn.append(" ").append(p.getLastName().trim());

        StringBuilder fullNameAr = new StringBuilder();
        if (p.getFirstNameAr()  != null) fullNameAr.append(p.getFirstNameAr().trim());
        if (p.getMiddleNameAr() != null && !p.getMiddleNameAr().isBlank())
            fullNameAr.append(" ").append(p.getMiddleNameAr().trim());
        if (p.getLastNameAr()   != null) fullNameAr.append(" ").append(p.getLastNameAr().trim());

        return EmployeeDetailResponse.builder()
            .employeeId(p.getEmployeeId())
            .employeeCode(p.getEmployeeCode())
            .firstName(p.getFirstName())
            .firstNameAr(p.getFirstNameAr())
            .middleName(p.getMiddleName())
            .middleNameAr(p.getMiddleNameAr())
            .lastName(p.getLastName())
            .lastNameAr(p.getLastNameAr())
            .fullNameEn(fullNameEn.toString().trim())
            .fullNameAr(fullNameAr.toString().trim())
            .dateOfBirth(p.getDateOfBirth())
            .gender(p.getGender())
            .bloodGroup(p.getBloodGroup())
            .maritalStatus(p.getMaritalStatus())
            .nationality(p.getNationality())
            .religion(p.getReligion())
            .profilePhotoUrl(p.getProfilePhotoUrl())
            .personalEmail(p.getPersonalEmail())
            .workEmail(p.getWorkEmail())
            .personalPhone(p.getPersonalPhone())
            .workPhone(p.getWorkPhone())
            .hireDate(p.getHireDate())
            .probationEndDate(p.getProbationEndDate())
            .confirmationDate(p.getConfirmationDate())
            .employmentStatus(p.getEmploymentStatus())
            .employmentType(p.getEmploymentType())
            .isActive(p.getIsActive() != null && p.getIsActive() == 1)
            .departmentId(p.getDepartmentId())
            .departmentName(p.getDepartmentName())
            .departmentCode(p.getDepartmentCode())
            .departmentNameAr(p.getDepartmentNameAr())
            .designationId(p.getDesignationId())
            .designationTitle(p.getDesignationTitle())
            .designationTitleAr(p.getDesignationTitleAr())
            .designationCode(p.getDesignationCode())
            .gradeLevel(p.getGradeLevel())
            .createdBy(p.getCreatedBy())
            .createdAt(p.getCreatedAt())
            .updatedBy(p.getUpdatedBy())
            .updatedAt(p.getUpdatedAt())
            .build();
    }

    private String buildFullName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        if (first  != null && !first.isBlank())  sb.append(first.trim());
        if (middle != null && !middle.isBlank()) sb.append(" ").append(middle.trim());
        if (last   != null && !last.isBlank())   sb.append(" ").append(last.trim());
        return sb.toString().trim();
    }

    private boolean isOnlyKeywordFilter(EmployeeFilterRequest filter) {
        return filter.getGender() == null
            && filter.getEmploymentStatus() == null
            && filter.getEmploymentType() == null
            && !StringUtils.hasText(filter.getNationality())
            && filter.getHireDateFrom() == null
            && filter.getHireDateTo() == null
            && filter.getDateOfBirthFrom() == null
            && filter.getDateOfBirthTo() == null;
    }

    private Pageable buildPageable(EmployeeFilterRequest filter) {
        int page = Math.max(0, filter.getPage());
        int size = Math.min(Math.max(1, filter.getSize()), 100);

        String sortField = ALLOWED_SORT_FIELDS.contains(filter.getSortBy())
            ? filter.getSortBy()
            : DEFAULT_SORT_FIELD;

        Sort.Direction direction = "desc".equalsIgnoreCase(filter.getSortDir())
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;

        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }

    private String getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "SYSTEM";
        return auth.getName();
    }

    private void validateUniqueFieldsForCreate(CreateEmployeeRequest request) {
        if (employeeRepository.existsByWorkEmailIgnoreCase(request.getWorkEmail())) {
            throw new DuplicateResourceException("Employee", "workEmail", request.getWorkEmail());
        }
        if (employeeRepository.existsByPersonalEmailIgnoreCase(request.getPersonalEmail())) {
            throw new DuplicateResourceException("Employee", "personalEmail", request.getPersonalEmail());
        }
    }

    private void validateUniqueFieldsForUpdate(Long id, UpdateEmployeeRequest request) {
        if (StringUtils.hasText(request.getPersonalEmail())
                && employeeRepository.existsByPersonalEmailIgnoreCaseAndIdNot(
                    request.getPersonalEmail(), id)) {
            throw new DuplicateResourceException("Employee", "personalEmail", request.getPersonalEmail());
        }
    }

    private void validateStatusTransition(Employee current, UpdateEmployeeRequest request) {
        if (request.getEmploymentStatus() == null) return;

        boolean currentlyExited = !current.getEmploymentStatus().isCurrentlyEmployed();
        boolean newStatusIsActive = request.getEmploymentStatus().isCurrentlyEmployed();

        if (currentlyExited && newStatusIsActive) {
            throw new BusinessRuleException(
                "INVALID_STATUS_TRANSITION",
                String.format(
                    "Cannot transition from '%s' to '%s' via a standard update. "
                    + "Use the reactivation endpoint for rehires.",
                    current.getEmploymentStatus().getDisplayName(),
                    request.getEmploymentStatus().getDisplayName()
                )
            );
        }
    }
}