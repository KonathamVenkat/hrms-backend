package com.hrms.leave.repository;

import com.hrms.leave.entity.LeaveRequestAttachmentContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeaveRequestAttachmentContentRepository
        extends JpaRepository<LeaveRequestAttachmentContent, Long> {
}
