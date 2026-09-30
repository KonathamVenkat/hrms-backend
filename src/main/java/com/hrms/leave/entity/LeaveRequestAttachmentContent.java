package com.hrms.leave.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * File bytes of a leave request's supporting document, in
 * HRMS.LEAVE_REQUEST_ATTACHMENT_CONTENT (primary key = the leave request's id).
 * The name and size stay on {@link LeaveRequest}; this row is only read on download.
 */
@Entity
@Table(name = "LEAVE_REQUEST_ATTACHMENT_CONTENT", schema = "HRMS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class LeaveRequestAttachmentContent {

    @Id
    @Column(name = "LEAVE_REQ_ID", nullable = false)
    private Long leaveReqId;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private byte[] content;
}
