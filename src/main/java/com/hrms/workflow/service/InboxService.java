package com.hrms.workflow.service;

import com.hrms.common.dto.PagedResponse;
import com.hrms.workflow.dto.response.InboxItemResponse;

public interface InboxService {

    /** The caller's waiting requests, oldest first. The caller comes from the security context; size is capped. */
    PagedResponse<InboxItemResponse> inbox(int page, int size);
}
