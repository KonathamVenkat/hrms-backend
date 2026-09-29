package com.hrms.leave.dto.response;

import org.springframework.core.io.Resource;

/** A leave request's stored attachment plus the original file name to download it as. */
public record LeaveAttachmentDownload(Resource resource, String fileName) {}
