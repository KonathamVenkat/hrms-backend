package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.employee.dto.request.BankBranchRequest;
import com.hrms.employee.dto.response.BankBranchResponse;
import com.hrms.employee.entity.Bank;
import com.hrms.employee.entity.BankBranch;
import com.hrms.employee.repository.BankBranchRepository;
import com.hrms.employee.repository.BankRepository;
import com.hrms.employee.service.impl.BankBranchServiceImpl;
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

/** Address, phone and email of a bank branch (V19): what is stored, what is refused, and that empty clears. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankBranchContactTest {

    @Mock BankBranchRepository branchRepository;
    @Mock BankRepository bankRepository;
    @Mock AuditTrail audit;
    @Mock EntityManager entityManager;

    BankBranchServiceImpl service;
    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @BeforeEach
    void setUp() {
        service = new BankBranchServiceImpl(branchRepository, bankRepository, audit, entityManager);
        Bank bank = mock(Bank.class);
        when(bank.getIsActive()).thenReturn(true);
        when(bankRepository.findById(1L)).thenReturn(Optional.of(bank));
        when(branchRepository.save(any(BankBranch.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BankBranchRequest.BankBranchRequestBuilder valid() {
        return BankBranchRequest.builder().bankId(1L).code("BR1").name("Main").nameAr("الرئيسي");
    }

    private Set<String> refusedFields(BankBranchRequest r) {
        return validator.validate(r).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void create_storesTheContactTrimmed_andTheEmailInLowerCase() {
        BankBranchResponse out = service.create(valid()
            .address("  Block 3, Khartoum  ").phone(" +249 123 456 ").email("  Branch@Bank.TEST ").build());

        assertEquals("Block 3, Khartoum", out.getAddress());
        assertEquals("+249 123 456", out.getPhone());
        assertEquals("branch@bank.test", out.getEmail());
    }

    @Test
    void create_withoutContactStoresNulls() {
        BankBranchResponse out = service.create(valid().address("   ").phone("").build());

        assertNull(out.getAddress());
        assertNull(out.getPhone());
        assertNull(out.getEmail());
    }

    @Test
    void update_overwritesTheContact_andAnEmptyValueClearsIt() {
        BankBranch row = BankBranch.builder().id(5L).bankId(1L).code("BR1").name("Main").nameAr("x").isActive(true)
            .version(2L).address("Old address").phone("123").email("old@bank.test").build();
        when(branchRepository.findById(5L)).thenReturn(Optional.of(row));

        service.update(5L, valid().version(2L).address("New address").phone("").email(null).build());

        assertEquals("New address", row.getAddress());
        assertNull(row.getPhone());
        assertNull(row.getEmail());
    }

    @Test
    void theAuditDetailCarriesNoContactDetails() {
        service.create(valid().address("Secret street 9").phone("999").email("x@bank.test").build());

        verify(audit).record(eq("BANK_BRANCH_CREATED"), anyString(), any(), argThat(detail ->
            !detail.contains("Secret") && !detail.contains("999") && !detail.contains("x@bank.test")));
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
