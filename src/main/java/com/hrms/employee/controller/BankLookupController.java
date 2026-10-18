package com.hrms.employee.controller;

import com.hrms.common.dto.ApiResponse;
import com.hrms.employee.dto.response.MasterLookupResponse;
import com.hrms.employee.service.BankBranchService;
import com.hrms.employee.service.BankService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only dropdowns of active banks and branches for the employee bank-account form (HR_ADMIN, who edits it). */
@RestController
@RequestMapping("/api/v1/banks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HR_ADMIN')")
public class BankLookupController {

    private final BankService bankService;
    private final BankBranchService branchService;

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> banks() {
        return ResponseEntity.ok(ApiResponse.<List<MasterLookupResponse>>builder()
            .success(true).message("Banks fetched successfully")
            .data(bankService.getActiveLookup()).statusCode(200).build());
    }

    @GetMapping("/{bankId}/branches/lookup")
    public ResponseEntity<ApiResponse<List<MasterLookupResponse>>> branches(@PathVariable Long bankId) {
        return ResponseEntity.ok(ApiResponse.<List<MasterLookupResponse>>builder()
            .success(true).message("Bank branches fetched successfully")
            .data(branchService.getActiveLookupByBankId(bankId)).statusCode(200).build());
    }
}
