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
    EmployeeAddressServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EmployeeAddressServiceImpl(addresses, employees);
        when(employees.existsById(5L)).thenReturn(true);
        when(addresses.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static EmployeeAddressRequest request(String type, Boolean primary) {
        return EmployeeAddressRequest.builder()
            .addressType(type).addressLine1(" 12 Main St ").city(" Muscat ").country("Oman")
            .isPrimary(primary).build();
    }

    private static EmployeeAddress address(long id, long employeeId, String type, int primary) {
        return EmployeeAddress.builder()
            .employeeAddressesId(id).employeeId(employeeId).addressType(type)
            .addressLine1("x").city("y").country("Oman").isPrimary(primary).isActive(1).build();
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
