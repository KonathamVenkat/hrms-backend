package com.hrms.employee.service.impl;

import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.EmployeeJobDetails;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.OrganisationLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganisationLookupImpl implements OrganisationLookup {

    private static final int CURRENT = 1;

    private final CompanyBranchRepository branchRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeJobDetailsRepository jobDetailsRepository;
    private final EmployeeRepository employeeRepository;
    private final com.hrms.auth.repository.AuthUserRepository authUserRepository;

    @Override
    public Optional<String> accountRoleOf(long employeeId) {
        return authUserRepository.findByEmployeeId(employeeId).map(u -> u.getRole().name());
    }

    @Override
    public Optional<Long> branchManagerOf(long employeeId) {
        return jobDetailsRepository.findByEmployeeIdAndIsCurrent(employeeId, CURRENT)
            .map(EmployeeJobDetails::getCompanyBranchId)
            .flatMap(branchRepository::findById)
            .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
            .map(CompanyBranch::getBranchManagerEmployeeId)
            .filter(this::isActiveEmployee);
    }

    @Override
    public Optional<Long> departmentOf(long employeeId) {
        return jobDetailsRepository.findByEmployeeIdAndIsCurrent(employeeId, CURRENT)
            .map(EmployeeJobDetails::getDepartmentId)
            .filter(id -> isDepartmentActive(id));
    }

    @Override
    public Optional<Long> headOf(long departmentId) {
        return personOf(departmentId, Department::getHeadEmployeeId);
    }

    @Override
    public Optional<Long> secretaryOf(long departmentId) {
        return personOf(departmentId, Department::getSecretaryEmployeeId);
    }

    @Override
    public boolean departmentExists(long departmentId) {
        return departmentRepository.existsById(departmentId);
    }

    @Override
    public boolean isDepartmentActive(long departmentId) {
        return departmentRepository.findById(departmentId).filter(OrganisationLookupImpl::active).isPresent();
    }

    private Optional<Long> personOf(long departmentId, Function<Department, Long> person) {
        return departmentRepository.findById(departmentId)
            .filter(OrganisationLookupImpl::active)
            .map(person)
            .filter(this::isActiveEmployee);
    }

    private static boolean active(Department d) {
        return Boolean.TRUE.equals(d.getIsActive());
    }

    private boolean isActiveEmployee(Long employeeId) {
        return employeeId != null && employeeRepository.existsByIdAndIsActive(employeeId, true);
    }
}
