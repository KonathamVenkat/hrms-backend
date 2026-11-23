package com.hrms.leave.service.impl;

import com.hrms.employee.entity.Employee;
import com.hrms.leave.entity.LeaveRequest;
import com.hrms.leave.entity.LeaveType;

import java.util.LinkedHashMap;
import java.util.Map;

/** The one-line description and requester name of a leave request, shared by the inbox and the workflow status. */
final class LeaveRequestSummary {

    private LeaveRequestSummary() {
    }

    static String text(LeaveRequest lr) {
        return lr.getLeaveTypeCode() + " " + lr.getStartDate() + " to " + lr.getEndDate() + " ("
            + lr.getTotalDays() + " days)";
    }

    /**
     * The same facts, language-neutral, for the screen to word in the user's language: the type code and its English
     * and Arabic names (names left out when the type is unknown), ISO dates and the day count.
     */
    static Map<String, Object> details(LeaveRequest lr, LeaveType type) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("leaveTypeCode", lr.getLeaveTypeCode());
        if (type != null) {
            d.put("leaveTypeNameEn", type.getNameEn());
            d.put("leaveTypeNameAr", type.getNameAr());
        }
        d.put("startDate", String.valueOf(lr.getStartDate()));
        d.put("endDate", String.valueOf(lr.getEndDate()));
        d.put("totalDays", lr.getTotalDays());
        return d;
    }

    static String name(Employee e) {
        return e == null ? null : e.getFirstName() + " " + e.getLastName();
    }
}
