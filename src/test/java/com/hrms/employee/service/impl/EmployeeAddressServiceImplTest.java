package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.EmployeeAddressRequest;
import com.hrms.employee.dto.response.EmployeeAddressResponse;
import com.hrms.employee.entity.EmployeeAddress;
import com.hrms.employee.repository.EmployeeAddressRepository;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmployeeAddressServiceImplTest {

    EmployeeAddressRepository addresses = mock(EmployeeAddressRepository.class);
    EmployeeRepository        employees = mock(EmployeeRepository.class);
    com.hrms.auth.service.AuditTrail audit = mock(com.hrms.auth.service.AuditTrail.class);
    EmployeeAddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeAddressServiceImpl(addresses, employees, audit);
        when(employees.findByIdForUpdate(5L)).thenReturn(java.util.Optional.of(new com.hrms.employee.entity.Employee()));
        when(addresses.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static EmployeeAddressRequest request(String type, Boolean primary) {
        return EmployeeAddressRequest.builder()
            .addressType(type).addressLine1(" 12 Main St ").city(" Muscat ").country("Oman")
            .isPrimary(primary).version(0L).build();
    }

    private static EmployeeAddress address(long id, long employeeId, String type, int primary) {
        return EmployeeAddress.builder()
            .employeeAddressesId(id).employeeId(employeeId).addressType(type)
            .addressLine1("x").city("y").country("Oman").isPrimary(primary).isActive(1).version(0L).build();
    }


    @Test
    void everyAddressChangeLocksTheEmployeeFirst() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 5, "CURRENT", 0)));
        when(addresses.countByEmployeeIdAndIsActive(5L, 1)).thenReturn(2L);

        service.addAddress(5L, request("MAILING", false));
        service.updateAddress(5L, 7L, request("CURRENT", false));
        service.setPrimary(5L, 7L);
        service.deactivateAddress(5L, 7L);

        verify(employees, times(4)).findByIdForUpdate(5L);
    }

    @Test
    void editingWithoutAVersionIsRefused_andOptionalTextIsTrimmedToNull() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 5, "CURRENT", 0)));
        EmployeeAddressRequest noVersion = request("CURRENT", false);
        noVersion.setVersion(null);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.updateAddress(5L, 7L, noVersion));
        assertEquals("VERSION_REQUIRED", ex.getRuleCode());

        EmployeeAddressRequest blanks = request("CURRENT", false);
        blanks.setAddressLine2("   ");
        blanks.setPostalCode(" 11111 ");
        EmployeeAddressResponse r = service.updateAddress(5L, 7L, blanks);
        assertNull(r.getAddressLine2());
        assertEquals("11111", r.getPostalCode());
    }

    @Test
    void editingFromAStaleScreenIsRefused() {
        EmployeeAddress current = address(7, 5, "CURRENT", 0);
        current.setVersion(2L);
        when(addresses.findById(7L)).thenReturn(Optional.of(current));
        EmployeeAddressRequest req = request("CURRENT", false);
        req.setVersion(1L);

        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
            () -> service.updateAddress(5L, 7L, req));
        verify(addresses, never()).save(any());
    }
    @Test
    void anUnknownEmployeeIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getAddresses(9L));
        assertThrows(ResourceNotFoundException.class, () -> service.addAddress(9L, request("CURRENT", false)));
    }

    @Test
    void rejectsASecondActiveAddressOfTheSameType() {
        when(addresses.existsByEmployeeIdAndAddressTypeAndIsActive(5L, "CURRENT", 1)).thenReturn(true);

        var ex = assertThrows(BusinessRuleException.class, () -> service.addAddress(5L, request("CURRENT", false)));

        assertEquals("DUPLICATE_ADDRESS_TYPE", ex.getRuleCode());
        verify(addresses, never()).save(any());
    }

    @Test
    void theFirstAddressBecomesPrimaryAndIsTrimmed() {
        when(addresses.countByEmployeeIdAndIsActive(5L, 1)).thenReturn(0L);

        EmployeeAddressResponse saved = service.addAddress(5L, request("PERMANENT", false));

        assertTrue(saved.getIsPrimary());
        assertEquals("12 Main St", saved.getAddressLine1());
        assertEquals("Muscat", saved.getCity());
        verify(addresses).unsetAllPrimary(5L);
    }

    @Test
    void aLaterAddressIsNotPrimaryUnlessAsked() {
        when(addresses.countByEmployeeIdAndIsActive(5L, 1)).thenReturn(1L);

        assertFalse(service.addAddress(5L, request("MAILING", false)).getIsPrimary());
        verify(addresses, never()).unsetAllPrimary(anyLong());

        assertTrue(service.addAddress(5L, request("EMERGENCY", true)).getIsPrimary());
        verify(addresses).unsetAllPrimary(5L);
    }

    @Test
    void anAddressOfAnotherEmployeeIsNotFound() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 6, "CURRENT", 0)));

        assertThrows(ResourceNotFoundException.class, () -> service.getAddressById(5L, 7L));
        assertThrows(ResourceNotFoundException.class, () -> service.setPrimary(5L, 7L));
    }

    @Test
    void aRemovedAddressCannotBecomeThePrimaryOne() {
        EmployeeAddress removed = address(7, 5, "CURRENT", 0);
        removed.setIsActive(0);
        when(addresses.findById(7L)).thenReturn(Optional.of(removed));

        var ex = assertThrows(BusinessRuleException.class, () -> service.setPrimary(5L, 7L));

        assertEquals("ADDRESS_INACTIVE", ex.getRuleCode());
        verify(addresses, never()).unsetAllPrimary(any());
        verify(addresses, never()).save(any());
    }

    @Test
    void addingChangingMakingPrimaryAndRemovingAnAddressAreAudited_withoutTheAddressText() {
        when(addresses.save(any())).thenAnswer(i -> {
            EmployeeAddress a = i.getArgument(0);
            if (a.getEmployeeAddressesId() == null) a.setEmployeeAddressesId(7L);
            return a;
        });
        service.addAddress(5L, request("CURRENT", false));
        verify(audit).record("EMPLOYEE_ADDRESS_ADDED", "EMPLOYEE_ADDRESS", 7L, "employeeId=5, type=CURRENT");

        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 5, "CURRENT", 0)));
        service.updateAddress(5L, 7L, request("CURRENT", false));
        verify(audit).record("EMPLOYEE_ADDRESS_UPDATED", "EMPLOYEE_ADDRESS", 7L, "employeeId=5, type=CURRENT");

        service.setPrimary(5L, 7L);
        verify(audit).record("EMPLOYEE_ADDRESS_PRIMARY_SET", "EMPLOYEE_ADDRESS", 7L, "employeeId=5, type=CURRENT");

        when(addresses.countByEmployeeIdAndIsActive(5L, 1)).thenReturn(2L);
        service.deactivateAddress(5L, 7L);
        verify(audit).record("EMPLOYEE_ADDRESS_DEACTIVATED", "EMPLOYEE_ADDRESS", 7L, "employeeId=5, type=CURRENT");

        var details = ArgumentCaptor.forClass(String.class);
        verify(audit, times(4)).record(anyString(), anyString(), any(), details.capture());
        details.getAllValues().forEach(d -> assertFalse(d.contains("Main St") || d.contains("Muscat")));
    }

    @Test
    void aRemovedAddressCannotBeEditedEither_soItCannotBecomePrimaryThroughAnUpdate() {
        EmployeeAddress removed = address(7, 5, "CURRENT", 0);
        removed.setIsActive(0);
        when(addresses.findById(7L)).thenReturn(Optional.of(removed));

        var ex = assertThrows(BusinessRuleException.class, () -> service.updateAddress(5L, 7L, request("CURRENT", true)));

        assertEquals("ADDRESS_INACTIVE", ex.getRuleCode());
        verify(addresses, never()).unsetAllPrimary(any());
        verify(addresses, never()).save(any());
    }

    @Test
    void removingAnAlreadyRemovedAddressChangesNothingAndIsNotAuditedAgain() {
        EmployeeAddress removed = address(7, 5, "CURRENT", 0);
        removed.setIsActive(0);
        when(addresses.findById(7L)).thenReturn(Optional.of(removed));

        service.deactivateAddress(5L, 7L);

        verify(addresses, never()).save(any());
        verifyNoInteractions(audit);
    }

    @Test
    void aRefusedChangeIsNotAudited() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 6, "CURRENT", 0))); // another employee's

        assertThrows(ResourceNotFoundException.class, () -> service.setPrimary(5L, 7L));

        verifyNoInteractions(audit);
    }

    @Test
    void settingPrimaryClearsTheOthersFirst() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 5, "CURRENT", 0)));

        service.setPrimary(5L, 7L);

        var order = inOrder(addresses);
        order.verify(addresses).unsetAllPrimary(5L);
        ArgumentCaptor<EmployeeAddress> saved = ArgumentCaptor.forClass(EmployeeAddress.class);
        order.verify(addresses).save(saved.capture());
        assertEquals(1, saved.getValue().getIsPrimary());
    }

    @Test
    void changingTheTypeToOneAlreadyInUseIsRejected() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 5, "CURRENT", 0)));
        when(addresses.existsByEmployeeIdAndAddressTypeAndIsActiveAndEmployeeAddressesIdNot(5L, "MAILING", 1, 7L))
            .thenReturn(true);

        var ex = assertThrows(BusinessRuleException.class,
            () -> service.updateAddress(5L, 7L, request("MAILING", false)));

        assertEquals("DUPLICATE_ADDRESS_TYPE", ex.getRuleCode());
    }

    @Test
    void theOnlyActiveAddressCannotBeDeactivated() {
        when(addresses.findById(7L)).thenReturn(Optional.of(address(7, 5, "CURRENT", 1)));
        when(addresses.countByEmployeeIdAndIsActive(5L, 1)).thenReturn(1L);

        var ex = assertThrows(BusinessRuleException.class, () -> service.deactivateAddress(5L, 7L));

        assertEquals("CANNOT_DELETE_LAST", ex.getRuleCode());
        verify(addresses, never()).save(any());
    }

    @Test
    void deactivatingThePrimaryPromotesAnotherActiveAddress() {
        EmployeeAddress primary = address(7, 5, "CURRENT", 1);
        EmployeeAddress other   = address(8, 5, "MAILING", 0);
        when(addresses.findById(7L)).thenReturn(Optional.of(primary));
        when(addresses.countByEmployeeIdAndIsActive(5L, 1)).thenReturn(2L);
        when(addresses.findByEmployeeIdAndIsActiveOrderByAddressTypeAsc(5L, 1))
            .thenReturn(List.of(primary, other));

        service.deactivateAddress(5L, 7L);

        assertEquals(0, primary.getIsActive());
        assertEquals(0, primary.getIsPrimary());
        assertEquals(1, other.getIsPrimary());
    }
}
