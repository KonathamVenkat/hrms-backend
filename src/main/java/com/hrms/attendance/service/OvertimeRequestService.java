package com.hrms.attendance.service;

import com.hrms.attendance.dto.request.OvertimeActionRequest;
import com.hrms.attendance.dto.request.OvertimeSubmitRequest;
import com.hrms.attendance.dto.response.OvertimeResponse;
import com.hrms.attendance.enums.RegularizationStatus;
import com.hrms.common.dto.PagedResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Service interface for Overtime Request operations.
 *
 * Lifecycle:
 *   submit → [PENDING] → approve → [APPROVED] (attendance log OT minutes updated)
 *                       → reject  → [REJECTED]
 *   employee cancel     → [CANCELLED] (only while PENDING)
 */
public interface OvertimeRequestService {

    // ── Employee operations ───────────────────────────────
    OvertimeResponse submit(OvertimeSubmitRequest request);

    OvertimeResponse cancel(String otId, Long employeeId);

    PagedResponse<OvertimeResponse> getMyRequests(Long employeeId, Pageable pageable);

    OvertimeResponse getById(String otId);

    // ── HR / Manager operations ───────────────────────────
    OvertimeResponse approve(String otId, OvertimeActionRequest request);

    OvertimeResponse reject(String otId, OvertimeActionRequest request);

    PagedResponse<OvertimeResponse> getAllRequests(
            RegularizationStatus status,
            Long employeeId,
            LocalDate from,
            LocalDate to,
            Pageable pageable);

    long getPendingCount();
}
