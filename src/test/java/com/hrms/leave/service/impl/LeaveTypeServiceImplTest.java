package com.hrms.leave.service.impl;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.employee.config.UploadLimits;
import com.hrms.leave.dto.request.LeaveTypeRequest;
import com.hrms.leave.dto.response.LeaveTypeResponse;
import com.hrms.leave.entity.LeaveType;
import com.hrms.leave.repository.LeaveBalanceRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.repository.LeaveTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Leave type rules: a code in use cannot be renamed, an edit needs the current version, every write is audited. */
@ExtendWith(MockitoExtension.class)
class LeaveTypeServiceImplTest {

    @Mock LeaveTypeRepository leaveTypes;
    @Mock LeaveRequestRepository requests;
    @Mock LeaveBalanceRepository balances;
    @Mock AuditTrail audit;

    LeaveTypeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LeaveTypeServiceImpl(leaveTypes, new UploadLimits(25), audit, requests, balances);
    }

    private LeaveType annual() {
        return LeaveType.builder().leaveTypeId(7L).code("ANNUAL").nameEn("Annual leave").nameAr("سنوية")
            .defaultDays(new BigDecimal("21")).applicableGender("ALL").isActive(1).version(3L).build();
    }

    private LeaveTypeRequest request(String code, Long version) {
        return LeaveTypeRequest.builder().code(code).nameEn("Annual leave").nameAr("سنوية")
            .defaultDays(new BigDecimal("21")).isPaid(true).isCarryForward(false).requiresDocument(false)
            .applicableGender("ALL").version(version).build();
    }

    private void found() {
        when(leaveTypes.findById(7L)).thenReturn(Optional.of(annual()));
    }

    private void saving() {
        when(leaveTypes.saveAndFlush(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void update_withoutAVersion_isRefused() {
        found();
        assertThrows(BadRequestException.class, () -> service.updateLeaveType(7L, request("ANNUAL", null)));
        verify(leaveTypes, never()).saveAndFlush(any());
    }

    @Test
    void update_withAStaleVersion_isRefusedAsAConflict() {
        found();
        assertThrows(OptimisticLockingFailureException.class, () -> service.updateLeaveType(7L, request("ANNUAL", 2L)));
        verify(leaveTypes, never()).saveAndFlush(any());
    }

    @Test
    void renamingTheCodeOfATypeThatHasRequests_isRefused() {
        found();
        when(requests.existsByLeaveTypeCode("ANNUAL")).thenReturn(true);

        BusinessRuleException e = assertThrows(BusinessRuleException.class,
            () -> service.updateLeaveType(7L, request("YEARLY", 3L)));

        assertEquals("LEAVE_TYPE_CODE_IN_USE", e.getRuleCode());
        verify(leaveTypes, never()).saveAndFlush(any());
    }

    @Test
    void renamingTheCodeOfATypeThatHasBalances_isRefused() {
        found();
        when(requests.existsByLeaveTypeCode("ANNUAL")).thenReturn(false);
        when(balances.existsByLeaveTypeCode("ANNUAL")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.updateLeaveType(7L, request("YEARLY", 3L)));
    }

    @Test
    void renamingTheCodeOfAnUnusedType_isAllowedAndAudited() {
        found();
        saving();
        when(requests.existsByLeaveTypeCode("ANNUAL")).thenReturn(false);
        when(balances.existsByLeaveTypeCode("ANNUAL")).thenReturn(false);

        LeaveTypeResponse saved = service.updateLeaveType(7L, request("YEARLY", 3L));

        assertEquals("YEARLY", saved.getCode());
        verify(audit).record(eq("LEAVE_TYPE_UPDATED"), eq("LEAVE_TYPE"), eq(7L), anyString());
    }

    @Test
    void changingOtherFieldsOfATypeInUse_isAllowedWithoutLookingForReferences() {
        found();
        saving();

        service.updateLeaveType(7L, request("ANNUAL", 3L));

        verify(requests, never()).existsByLeaveTypeCode(anyString());
        verify(audit).record(eq("LEAVE_TYPE_UPDATED"), eq("LEAVE_TYPE"), eq(7L), anyString());
    }

    @Test
    void create_isAudited() {
        when(leaveTypes.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(leaveTypes.existsByNameEnIgnoreCase(anyString())).thenReturn(false);
        when(leaveTypes.saveAndFlush(any(LeaveType.class))).thenAnswer(i -> {
            LeaveType t = i.getArgument(0);
            t.setLeaveTypeId(9L);
            return t;
        });

        service.createLeaveType(request("STUDY2", null));

        verify(audit).record(eq("LEAVE_TYPE_CREATED"), eq("LEAVE_TYPE"), eq(9L), anyString());
    }

    @Test
    void deactivateAndActivate_areAudited() {
        found();
        when(leaveTypes.save(any(LeaveType.class))).thenAnswer(i -> i.getArgument(0));

        service.deactivateLeaveType(7L);
        service.activateLeaveType(7L);

        verify(audit).record("LEAVE_TYPE_DEACTIVATED", "LEAVE_TYPE", 7L, "ANNUAL");
        verify(audit).record("LEAVE_TYPE_ACTIVATED", "LEAVE_TYPE", 7L, "ANNUAL");
    }
}
