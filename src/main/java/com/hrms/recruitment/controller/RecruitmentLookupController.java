package com.hrms.recruitment.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.recruitment.dto.response.RecruitmentLookupResponse;
import com.hrms.recruitment.enums.ReasonScope;
import com.hrms.recruitment.service.RejectionReasonService;
import com.hrms.recruitment.service.StageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only dropdowns for HR screens. Active rows only. */
@RestController
@RequestMapping("/api/v1/recruitment/lookups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('HR_ADMIN','HR_MANAGER')")
public class RecruitmentLookupController {

    private final StageService stages;
    private final RejectionReasonService reasons;

    @GetMapping("/stages")
    public ResponseEntity<ApiResponse<List<RecruitmentLookupResponse>>> stages() {
        return ResponseEntity.ok(ApiResponse.success("Stages fetched", stages.getActiveLookup()));
    }

    @GetMapping("/rejection-reasons")
    public ResponseEntity<ApiResponse<List<RecruitmentLookupResponse>>> rejectionReasons(
            @RequestParam(defaultValue = "APPLICATION") ReasonScope appliesTo) {
        return ResponseEntity.ok(ApiResponse.success("Rejection reasons fetched", reasons.getActiveLookup(appliesTo)));
    }
}
