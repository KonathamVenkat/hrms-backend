package com.hrms.attendance.dto.response;

/** What generating the absent / weekend / holiday / leave day records changed. */
public record DayRecordsResult(int created, int corrected, int employeesRefreshed) {}
