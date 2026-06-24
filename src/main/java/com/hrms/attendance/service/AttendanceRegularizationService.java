package com.hrms.attendance.service;

import com.hrms.attendance.dto.request.RegularizationActionRequest;
import com.hrms.attendance.dto.request.RegularizationRequest;
import com.hrms.attendance.dto.response.RegularizationResponse;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.common.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Service interface for Attendance Regularization operations.
 *
 * Lifecycle:
 *   submit → [PENDING] → approve → [APPROVED] (attendance log corrected)
 *                      → reject  → [REJECTED]
 *   employee cancel    → [CANCELLED] (only while PENDING)
 */
public interface AttendanceRegularizationService {

    // ── Employee operations ───────────────────────────────────
    RegularizationResponse submit(RegularizationRequest request);

    RegularizationResponse cancel(Long regId, Long employeeId);

    PagedResponse<RegularizationResponse> getMyRequests(
            Long employeeId, Pageable pageable);

    RegularizationResponse getById(Long regId);

    // ── HR / Manager operations ───────────────────────────────
    RegularizationResponse approve(Long regId, RegularizationActionRequest request);

    RegularizationResponse reject(Long regId, RegularizationActionRequest request);

    PagedResponse<RegularizationResponse> getAllRequests(
            RegularizationStatus status,
            Long employeeId,
            LocalDate from,
            LocalDate to,
            Pageable pageable);

    long getPendingCount();
}
