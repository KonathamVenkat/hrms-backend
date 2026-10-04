package com.hrms.attendance.service.impl;

import com.hrms.attendance.entity.AttendanceLog;
import com.hrms.attendance.enums.AttendanceStatus;
import com.hrms.attendance.repository.AttendanceLogRepository;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes the attendance row for every day an employee did not punch: WEEKEND, HOLIDAY, ON_LEAVE
 * or ABSENT. Without these rows an absent employee simply has no data, so the monthly summary
 * could never show an absent day.
 *
 * <p>A generated row has no check-in time and is not regularized. That is what tells it apart from
 * a real punch, so it is safe to recompute: a leave approved or a holiday added after the row was
 * written corrects it on the next run. A real punch, or a corrected one, is never touched.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceDayRecorder {

    /** What one run changed. */
    public record Result(int created, int updated, Set<Long> employeeIds) {
        public static Result empty() { return new Result(0, 0, Set.of()); }
    }

    static final String SYSTEM = "SYSTEM";

    private final EmployeeRepository      employeeRepo;
    private final AttendanceLogRepository logRepo;
    private final AttendanceCalculator    calculator;
    private final AttendanceDayClassifier classifier;

    /**
     * Writes or corrects the rows for {@code date}, which must be a finished day (before today):
     * today is still in progress and its absentees may yet arrive.
     */
    public Result generateFor(LocalDate date) {
        if (!date.isBefore(LocalDate.now())) {
            log.debug("Skipping day records for {}: the day is not over", date);
            return Result.empty();
        }

        boolean holiday = classifier.isPublicHoliday(date);
        Map<Long, AttendanceDayClassifier.LeaveCover> leave = classifier.leaveCoverByEmployee(date);
        Map<Long, AttendanceLog> existing = logRepo.findByAttendanceDateAndIsActive(date, 1).stream()
                .collect(Collectors.toMap(AttendanceLog::getEmployeeId, Function.identity(), (a, b) -> a));

        int created = 0;
        int updated = 0;
        Set<Long> touched = new HashSet<>();

        for (Employee employee : employeeRepo.findByIsActiveAndHireDateLessThanEqual(true, date)) {
            AttendanceLog row = existing.get(employee.getId());
            if (row != null && !isGenerated(row)) continue; // a real or corrected punch

            WorkShift shift = calculator.resolveShift(employee.getId());
            AttendanceStatus status = classifier.statusWithoutPunch(shift, date, holiday,
                    leave.getOrDefault(employee.getId(), AttendanceDayClassifier.LeaveCover.NONE));

            if (row == null) {
                logRepo.save(newRow(employee, date, status, leave.get(employee.getId())));
                created++;
                touched.add(employee.getId());
            } else if (row.getStatus() != status) {
                row.setStatus(status);
                row.setNotes(note(status, leave.get(employee.getId())));
                row.setUpdatedAt(LocalDateTime.now());
                logRepo.save(row);
                updated++;
                touched.add(employee.getId());
            }
        }
        log.info("Attendance day records for {}: {} created, {} corrected", date, created, updated);
        return new Result(created, updated, touched);
    }

    /** A row this class wrote: nobody punched and nobody regularized it. */
    static boolean isGenerated(AttendanceLog row) {
        return row.getCheckInTime() == null && row.getIsRegularized() != null && row.getIsRegularized() == 0;
    }

    private AttendanceLog newRow(Employee employee, LocalDate date, AttendanceStatus status,
                                 AttendanceDayClassifier.LeaveCover leave) {
        LocalDateTime now = LocalDateTime.now();
        AttendanceLog row = AttendanceLog.builder()
                .logId(logRepo.findNextSequenceValue())
                .employeeId(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .attendanceDate(date)
                .status(status)
                .punchSource(null)
                .notes(note(status, leave))
                .build();
        row.setCreatedBy(SYSTEM);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private static String note(AttendanceStatus status, AttendanceDayClassifier.LeaveCover leave) {
        String detail = status == AttendanceStatus.ON_LEAVE && leave == AttendanceDayClassifier.LeaveCover.HALF
                ? "half-day leave, no punch for the rest of the day"
                : status.name().toLowerCase().replace('_', ' ');
        return "Generated by the system: " + detail;
    }
}
