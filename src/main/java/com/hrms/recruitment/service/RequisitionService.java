package com.hrms.recruitment.service;

import com.hrms.common.dto.PagedResponse;
import com.hrms.recruitment.dto.request.ApproveRequisitionRequest;
import com.hrms.recruitment.dto.request.RejectRequisitionRequest;
import com.hrms.recruitment.dto.request.RequisitionActionRequest;
import com.hrms.recruitment.dto.request.RequisitionRequest;
import com.hrms.recruitment.dto.response.PersonOptionResponse;
import com.hrms.recruitment.dto.response.RaiseOptionsResponse;
import com.hrms.recruitment.dto.response.RequisitionResponse;
import com.hrms.recruitment.dto.response.RequisitionSummaryResponse;
import com.hrms.recruitment.enums.RequisitionStatus;

import java.util.List;

public interface RequisitionService {

    /** What the caller may pick on the form: the departments they may raise for, with their designations and the locations. */
    RaiseOptionsResponse raiseOptions();

    /** Active employees of a department the caller may raise for, for the manager and replaced-employee pickers. */
    List<PersonOptionResponse> peopleOf(Long departmentId);

    RequisitionResponse create(RequisitionRequest request);

    RequisitionResponse update(Long id, RequisitionRequest request);

    RequisitionResponse get(Long id);

    /** HR sees every requisition; anyone else sees the ones they raised or that belong to a department they head. */
    PagedResponse<RequisitionSummaryResponse> search(RequisitionStatus status, Long departmentId, String q,
                                                     int page, int size, String sortBy, String direction);

    RequisitionResponse submit(Long id, RequisitionActionRequest request);

    RequisitionResponse approve(Long id, ApproveRequisitionRequest request);

    RequisitionResponse reject(Long id, RejectRequisitionRequest request);

    RequisitionResponse hold(Long id, RequisitionActionRequest request);

    RequisitionResponse resume(Long id, RequisitionActionRequest request);

    RequisitionResponse close(Long id, RequisitionActionRequest request);

    RequisitionResponse cancel(Long id, RequisitionActionRequest request);
}
