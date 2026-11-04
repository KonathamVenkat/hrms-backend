package com.hrms.employee.service.impl;

import com.hrms.employee.dto.response.EmployeeCompletenessResponse;
import com.hrms.employee.repository.EmployeeAddressRepository;
import com.hrms.employee.repository.EmployeeBankAccountRepository;
import com.hrms.employee.repository.EmployeeDependentRepository;
import com.hrms.employee.repository.EmployeeDocumentRepository;
import com.hrms.employee.repository.EmployeeEducationRepository;
import com.hrms.employee.repository.EmployeeExperienceRepository;
import com.hrms.employee.repository.EmployeeIdentityInfoRepository;
import com.hrms.employee.repository.EmployeeJobDetailsRepository;
import com.hrms.employee.repository.EmployeeLanguageRepository;
import com.hrms.employee.repository.EmployeeReferenceRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.EmployeeTrainingRepository;
import com.hrms.employee.service.EmployeeChecks;
import com.hrms.employee.service.EmployeeCompletenessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Existence checks only (one EXISTS per table, no rows are loaded). The rules mirror what each tab lists: a current
 * job, any address, bank account, dependant, education, training, language, experience or reference row, an identity
 * record, and an active (not removed) document.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeCompletenessServiceImpl implements EmployeeCompletenessService {

    private static final int YES = 1;

    private final EmployeeRepository employeeRepository;
    private final EmployeeJobDetailsRepository jobs;
    private final EmployeeAddressRepository addresses;
    private final EmployeeIdentityInfoRepository identities;
    private final EmployeeBankAccountRepository bankAccounts;
    private final EmployeeDocumentRepository documents;
    private final EmployeeDependentRepository dependants;
    private final EmployeeEducationRepository education;
    private final EmployeeTrainingRepository training;
    private final EmployeeLanguageRepository languages;
    private final EmployeeExperienceRepository experience;
    private final EmployeeReferenceRepository references;

    @Override
    public EmployeeCompletenessResponse getCompleteness(Long employeeId) {
        EmployeeChecks.requireExists(employeeRepository, employeeId);
        return new EmployeeCompletenessResponse(
            jobs.existsByEmployeeIdAndIsCurrent(employeeId, YES),
            addresses.existsByEmployeeId(employeeId),
            identities.existsByEmployeeId(employeeId),
            bankAccounts.existsByEmployeeId(employeeId),
            documents.existsByEmployeeIdAndIsActive(employeeId, YES),
            dependants.existsByEmployeeId(employeeId),
            education.existsByEmployeeId(employeeId)
                && training.existsByEmployeeId(employeeId)
                && languages.existsByEmployeeId(employeeId),
            experience.existsByEmployeeId(employeeId) && references.existsByEmployeeId(employeeId));
    }
}
