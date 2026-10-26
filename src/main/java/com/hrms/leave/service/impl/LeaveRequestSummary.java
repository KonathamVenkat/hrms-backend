package com.hrms.leave.service.impl;

import com.hrms.employee.entity.Employee;
import com.hrms.leave.entity.LeaveRequest;

/** The one-line description and requester name of a leave request, shared by the inbox and the workflow status. */
final class LeaveRequestSummary {

    private LeaveRequestSummary() {
    }

    static String text(LeaveRequest lr) {
        return lr.getLeaveTypeCode() + " " + lr.getStartDate() + " to " + lr.getEndDate() + " ("
            + lr.getTotalDays() + " days)";
    }

    static String name(Employee e) {
        return e == null ? null : e.getFirstName() + " " + e.getLastName();
    }
}
