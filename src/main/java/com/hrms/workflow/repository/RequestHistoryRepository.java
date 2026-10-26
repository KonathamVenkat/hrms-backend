package com.hrms.workflow.repository;

import com.hrms.workflow.entity.RequestHistory;
import com.hrms.workflow.enums.RequestType;
import org.springframework.data.repository.Repository;

import java.util.List;

/** Append-only by type: no delete, no update, no findAll. */
public interface RequestHistoryRepository extends Repository<RequestHistory, Long> {

    <S extends RequestHistory> S save(S entry);

    List<RequestHistory> findByRequestTypeAndRequestIdOrderByActedAtAscHistoryIdAsc(RequestType requestType, Long requestId);

    /** Whether the employee acted on this request before (a past participant may still see it, spec 10). */
    boolean existsByRequestTypeAndRequestIdAndActorEmployeeId(RequestType requestType, Long requestId,
                                                               Long actorEmployeeId);

    /** Oldest first, capped: a request has a handful of rows, the cap only bounds a pathological one. */
    List<RequestHistory> findTop200ByRequestTypeAndRequestIdOrderByActedAtAscHistoryIdAsc(RequestType requestType,
                                                                                         Long requestId);
}
