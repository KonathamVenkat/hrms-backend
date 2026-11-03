package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.employee.dto.request.CompanyBranchRequest;
import com.hrms.employee.dto.response.CompanyBranchResponse;
import com.hrms.employee.entity.Company;
import com.hrms.employee.entity.CompanyBranch;
import com.hrms.employee.repository.CompanyBranchRepository;
import com.hrms.employee.repository.CompanyRepository;
import com.hrms.employee.repository.DirectorateRepository;
import com.hrms.employee.service.impl.CompanyBranchServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Address, phone and email of a company branch (V20): what is stored, what is refused, and that empty clears. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyBranchContactTest {

    @Mock CompanyBranchRepository branchRepository;
    @Mock CompanyRepository companyRepository;
    @Mock DirectorateRepository directorateRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    CompanyBranchServiceImpl service;
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        service = new CompanyBranchServiceImpl(branchRepository, companyRepository, directorateRepository, audit, entityManager);
        Company company = mock(Company.class);
        when(company.getIsActive()).thenReturn(true);
        when(companyRepository.findById(1L)).thenReturn(Optional.of(company));
        when(branchRepository.save(any(CompanyBranch.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CompanyBranchRequest.CompanyBranchRequestBuilder valid() {
        return CompanyBranchRequest.builder().companyId(1L).code("BR1").name("Main").nameAr("الرئيسي");
    }

    private Set<String> refusedFields(CompanyBranchRequest r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void create_storesTheContactTrimmed_andTheEmailInLowerCase() {
        CompanyBranchResponse out = service.create(valid()
            .address("  Block 3, Khartoum  ").phone(" +249 123 456 ").email("  Branch@Co.TEST ").build());

        assertEquals("Block 3, Khartoum", out.getAddress());
        assertEquals("+249 123 456", out.getPhone());
        assertEquals("branch@co.test", out.getEmail());
    }

    @Test
    void create_withoutContactStoresNulls() {
        CompanyBranchResponse out = service.create(valid().address("   ").phone("").build());

        assertNull(out.getAddress());
        assertNull(out.getPhone());
        assertNull(out.getEmail());
    }

    @Test
    void update_overwritesTheContact_andAnEmptyValueClearsIt() {
        CompanyBranch row = CompanyBranch.builder().id(5L).companyId(1L).code("BR1").name("Main").nameAr("x").isActive(true)
            .version(2L).address("Old address").phone("123").email("old@co.test").build();
        when(branchRepository.findById(5L)).thenReturn(Optional.of(row));

        service.update(5L, valid().version(2L).address("New address").phone("").email(null).build());

        assertEquals("New address", row.getAddress());
        assertNull(row.getPhone());
        assertNull(row.getEmail());
    }

    @Test
    void theAuditDetailCarriesNoContactDetails() {
        service.create(valid().address("Secret street 9").phone("999").email("x@co.test").build());

        verify(audit).record(eq("COMPANY_BRANCH_CREATED"), anyString(), any(), argThat(detail ->
            !detail.contains("Secret") && !detail.contains("999") && !detail.contains("x@co.test")));
    }

    @Test
    void validation_acceptsAFullContact_andAnEmptyOne() {
        assertTrue(refusedFields(valid().address("Block 3").phone("+249 (1) 23-45").email("a@b.test").build()).isEmpty());
        assertTrue(refusedFields(valid().build()).isEmpty());
    }

    @Test
    void validation_refusesAPhoneWithLetters_aBadEmail_andControlCharactersInTheAddress() {
        assertEquals(Set.of("phone"), refusedFields(valid().phone("12ab").build()));
        assertEquals(Set.of("email"), refusedFields(valid().email("not-an-email").build()));
        assertEquals(Set.of("address"), refusedFields(valid().address("line\u0000break").build()));
        assertEquals(Set.of("address"), refusedFields(valid().address("a​b").build()));   // invisible character
    }

    @Test
    void validation_refusesValuesOverTheColumnLengths_andAcceptsTheLimits() {
        assertEquals(Set.of("address"), refusedFields(valid().address("x".repeat(301)).build()));
        assertEquals(Set.of("phone"), refusedFields(valid().phone("1".repeat(31)).build()));
        assertEquals(Set.of("email"), refusedFields(valid().email("a".repeat(195) + "@b.test").build()));
        assertTrue(refusedFields(valid().address("x".repeat(300)).phone("1".repeat(30)).build()).isEmpty());
    }
}
