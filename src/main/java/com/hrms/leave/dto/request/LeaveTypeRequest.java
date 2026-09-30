package com.hrms.leave.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveTypeRequest {

 @NotBlank(message = "Code is required")
 @Size(max = 30, message = "Code max 30 characters")
 @Pattern(regexp = "^[A-Z0-9_]+$",
          message = "Code must be uppercase letters, numbers and underscores only")
 private String code;

 @NotBlank(message = "English name is required")
 @Size(max = 100, message = "Name max 100 characters")
 private String nameEn;

 @NotBlank(message = "Arabic name is required")
 @Size(max = 200, message = "Arabic name max 200 characters")
 private String nameAr;

 @Size(max = 500, message = "Description max 500 characters")
 private String description;

 @NotNull(message = "Default days is required")
 @DecimalMin(value = "0", message = "Default days cannot be negative")
 @DecimalMax(value = "365", message = "Default days cannot exceed 365")
 private BigDecimal defaultDays;

 @NotNull(message = "Is paid field is required")
 private Boolean isPaid;

 @NotNull(message = "Is carry forward field is required")
 private Boolean isCarryForward;

 @DecimalMin(value = "0", message = "Max carry days cannot be negative")
 private BigDecimal maxCarryDays;

 @NotNull(message = "Requires document field is required")
 private Boolean requiresDocument;

 // The upper bound is the global upload cap (hrms.upload.max-file-size-mb), checked in the service.
 @Min(value = 1,  message = "Max file size must be at least 1 MB")
 private Integer docMaxFileSizeMb;

 @Size(max = 100)
 @Pattern(regexp = "^\\s*(?i:(pdf|jpg|jpeg|png))(\\s*,\\s*(?i:(pdf|jpg|jpeg|png)))*\\s*$",
          message = "Allowed extensions must be a comma-separated list of PDF, JPG, JPEG, PNG")
 private String docAllowedExtensions;

 @Min(value = 0, message = "Min notice days cannot be negative")
 private Integer minNoticeDays;

 @Min(value = 0, message = "Max consecutive days cannot be negative")
 private Integer maxConsecutiveDays;

 @NotBlank(message = "Applicable gender is required")
 @Pattern(regexp = "^(ALL|MALE|FEMALE)$",
          message = "Applicable gender must be ALL, MALE, or FEMALE")
 private String applicableGender;

 @Min(value = 0)
 private Integer sortOrder;

 private Boolean isActive;
}


