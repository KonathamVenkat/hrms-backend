package com.hrms.dashboard.service.impl;

import com.hrms.attendance.service.AttendanceRegularizationService;
import com.hrms.attendance.service.OvertimeRequestService;
import com.hrms.dashboard.dto.response.HrDashboardResponse;
import com.hrms.dashboard.service.DashboardService;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.leave.repository.LeaveRequestRepository;
import com.hrms.leave.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final EmployeeRepository                employeeRepository;
    private final LeaveRequestRepository            leaveRequestRepository;
    private final LeaveService                      leaveService;
    private final AttendanceRegularizationService   regularizationService;
    private final OvertimeRequestService            overtimeService;

    @Override
    public HrDashboardResponse getHrSummary() {
        LocalDate today     = LocalDate.now();
        // Company week runs Sunday–Saturday (Friday/Saturday weekend)
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate weekEnd   = weekStart.plusDays(6);

        return new HrDashboardResponse(
                employeeRepository.countCurrentlyEmployed(),
                employeeRepository.countByHireDateBetweenAndIsActive(today, today, true),
                employeeRepository.countByHireDateBetweenAndIsActive(weekStart, weekEnd, true),
                leaveRequestRepository.countEmployeesOnApprovedLeave(today),
                leaveService.getPendingCount(),
                regularizationService.getPendingCount(),
                overtimeService.getPendingCount());
    }
}
