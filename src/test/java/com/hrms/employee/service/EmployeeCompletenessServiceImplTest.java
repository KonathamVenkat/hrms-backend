package com.hrms.employee.service;

import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.response.EmployeeCompletenessResponse;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.EmployeeCompletenessServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** How the eleven answers of the single query become the eight tabs. The SQL itself is run by EmployeeCompletenessDbTest. */
class EmployeeCompletenessServiceImplTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final Query query = mock(Query.class);

    private final EmployeeCompletenessServiceImpl service =
        new EmployeeCompletenessServiceImpl(employees, entityManager);

    /** Columns: job, addresses, identity, bank, documents, dependants, education, training, languages, experience, references. */
    private EmployeeCompletenessResponse answerFor(Object... columns) {
        when(employees.existsById(5L)).thenReturn(true);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter("id", 5L)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(columns);
        return service.getCompleteness(5L);
    }

    @Test
    void aNewEmployeeHasNothingFilledIn() {
        assertThat(answerFor(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
            .isEqualTo(new EmployeeCompletenessResponse(false, false, false, false, false, false, false, false));
    }

    @Test
    void eachTabReportsItsOwnColumn() {
        EmployeeCompletenessResponse r = answerFor(1, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0);

        assertThat(r.job()).isTrue();
        assertThat(r.addresses()).isTrue();
        assertThat(r.identity()).isFalse();
        assertThat(r.bank()).isFalse();
        assertThat(r.documents()).isTrue();
        assertThat(r.family()).isTrue();
    }

    @Test
    void qualificationsNeedEducationTrainingAndLanguages() {
        assertThat(answerFor(0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 0).qualifications()).isFalse();
        assertThat(answerFor(0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0).qualifications()).isTrue();
    }

    @Test
    void experienceNeedsPastEmployersAndReferences() {
        assertThat(answerFor(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0).experience()).isFalse();
        assertThat(answerFor(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1).experience()).isTrue();
    }

    @Test
    void anUnknownEmployeeIsNotFoundAndNeverQueried() {
        when(employees.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> service.getCompleteness(9L)).isInstanceOf(ResourceNotFoundException.class);
        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void withoutHrOnlyTabsLeavesBankAndDocumentsOut() {
        EmployeeCompletenessResponse hidden = answerFor(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1).withoutHrOnlyTabs();

        assertThat(hidden.bank()).isNull();
        assertThat(hidden.documents()).isNull();
        assertThat(hidden.job()).isTrue();
        assertThat(hidden.qualifications()).isTrue();
    }
}
