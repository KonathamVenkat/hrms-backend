package com.hrms.employee.service;

import com.hrms.common.exception.ResourceNotFoundException;
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
import com.hrms.employee.service.impl.EmployeeCompletenessServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmployeeCompletenessServiceImplTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeJobDetailsRepository jobs = mock(EmployeeJobDetailsRepository.class);
    private final EmployeeAddressRepository addresses = mock(EmployeeAddressRepository.class);
    private final EmployeeIdentityInfoRepository identities = mock(EmployeeIdentityInfoRepository.class);
    private final EmployeeBankAccountRepository bank = mock(EmployeeBankAccountRepository.class);
    private final EmployeeDocumentRepository documents = mock(EmployeeDocumentRepository.class);
    private final EmployeeDependentRepository dependants = mock(EmployeeDependentRepository.class);
    private final EmployeeEducationRepository education = mock(EmployeeEducationRepository.class);
    private final EmployeeTrainingRepository training = mock(EmployeeTrainingRepository.class);
    private final EmployeeLanguageRepository languages = mock(EmployeeLanguageRepository.class);
    private final EmployeeExperienceRepository experience = mock(EmployeeExperienceRepository.class);
    private final EmployeeReferenceRepository references = mock(EmployeeReferenceRepository.class);

    private final EmployeeCompletenessServiceImpl service = new EmployeeCompletenessServiceImpl(
        employees, jobs, addresses, identities, bank, documents, dependants, education, training, languages,
        experience, references);

    @Test
    void aNewEmployeeHasNothingFilledIn() {
        when(employees.existsById(5L)).thenReturn(true);

        assertThat(service.getCompleteness(5L))
            .isEqualTo(new EmployeeCompletenessResponse(false, false, false, false, false, false, false, false));
    }

    @Test
    void eachTabReportsItsOwnRecords() {
        when(employees.existsById(5L)).thenReturn(true);
        when(jobs.existsByEmployeeIdAndIsCurrent(5L, 1)).thenReturn(true);
        when(addresses.existsByEmployeeId(5L)).thenReturn(true);
        when(documents.existsByEmployeeIdAndIsActive(5L, 1)).thenReturn(true);
        when(dependants.existsByEmployeeId(5L)).thenReturn(true);

        EmployeeCompletenessResponse r = service.getCompleteness(5L);

        assertThat(r.job()).isTrue();
        assertThat(r.addresses()).isTrue();
        assertThat(r.documents()).isTrue();
        assertThat(r.family()).isTrue();
        assertThat(r.identity()).isFalse();
        assertThat(r.bank()).isFalse();
    }

    @Test
    void qualificationsNeedEducationTrainingAndLanguages() {
        when(employees.existsById(5L)).thenReturn(true);
        when(education.existsByEmployeeId(5L)).thenReturn(true);
        when(training.existsByEmployeeId(5L)).thenReturn(true);
        assertThat(service.getCompleteness(5L).qualifications()).isFalse();

        when(languages.existsByEmployeeId(5L)).thenReturn(true);
        assertThat(service.getCompleteness(5L).qualifications()).isTrue();
    }

    @Test
    void experienceNeedsPastEmployersAndReferences() {
        when(employees.existsById(5L)).thenReturn(true);
        when(experience.existsByEmployeeId(5L)).thenReturn(true);
        assertThat(service.getCompleteness(5L).experience()).isFalse();

        when(references.existsByEmployeeId(5L)).thenReturn(true);
        assertThat(service.getCompleteness(5L).experience()).isTrue();
    }

    @Test
    void anUnknownEmployeeIsNotFound() {
        when(employees.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.getCompleteness(9L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
