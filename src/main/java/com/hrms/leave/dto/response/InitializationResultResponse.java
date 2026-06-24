package com.hrms.leave.dto.response;

import lombok.*;
import java.util.List;

/**
 * Result of a bulk balance initialization operation.
 */
@Data @Builder
public class InitializationResultResponse {

    private Integer year;
    private Integer totalEmployees;
    private Integer initializedCount;
    private Integer skippedCount;
    private Integer errorCount;
    private List<String> errors;
    private String  processedAt;
}
