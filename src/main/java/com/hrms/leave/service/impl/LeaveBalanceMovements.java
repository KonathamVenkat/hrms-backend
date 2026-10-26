package com.hrms.leave.service.impl;

import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.leave.entity.LeaveBalance;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.repository.LeaveBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The balance arithmetic of a decided leave request, written once for the legacy approve/reject/cancel and the
 * workflow's completion handler. Every method joins the caller's transaction.
 */
@Component
@RequiredArgsConstructor
class LeaveBalanceMovements {

    private final LeaveBalanceRepository balances;

    /** Pending days become used days. */
    LeaveBalance consume(LeaveRequest lr) {
        LeaveBalance balance = require(lr);
        balance.setPendingDays(Math.max(0, balance.getPendingDays() - lr.getTotalDays()));
        balance.setUsedDays(balance.getUsedDays() + lr.getTotalDays());
        return store(balance);
    }

    /** Pending days go back to available; a missing balance row is an error (reject). */
    LeaveBalance release(LeaveRequest lr) {
        LeaveBalance balance = require(lr);
        balance.setPendingDays(Math.max(0, balance.getPendingDays() - lr.getTotalDays()));
        return store(balance);
    }

    /** Same as {@link #release} but a missing balance row is tolerated (cancel). */
    void releaseIfPresent(LeaveRequest lr) {
        find(lr).ifPresent(balance -> {
            balance.setPendingDays(Math.max(0, balance.getPendingDays() - lr.getTotalDays()));
            store(balance);
        });
    }

    private LeaveBalance require(LeaveRequest lr) {
        return find(lr).orElseThrow(() -> new ResourceNotFoundException(
            "LeaveBalance", "employeeId", lr.getEmployeeId()));
    }

    private Optional<LeaveBalance> find(LeaveRequest lr) {
        return balances.findByEmployeeIdAndLeaveTypeCodeAndYear(
            lr.getEmployeeId(), lr.getLeaveTypeCode(), lr.getStartDate().getYear());
    }

    private LeaveBalance store(LeaveBalance balance) {
        balance.setUpdatedAt(LocalDateTime.now());
        return balances.save(balance);
    }
}
