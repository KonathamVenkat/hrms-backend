package com.hrms.recruitment.dto.response;

import java.util.List;

/**
 * What the signed-in user may pick on the requisition form. HR gets every active department; a department head gets
 * only the departments they head (and none at all when they head none). The employee-module lookups are HR-only, so
 * a head could not fill the form without this.
 */
public record RaiseOptionsResponse(
    List<Option> departments,
    List<DesignationOption> designations,
    List<Option> locations
) {
    public record Option(Long id, String name, String nameAr) { }

    /** {@code departmentId} is null for a designation that belongs to no department and so fits any of them. */
    public record DesignationOption(Long id, String title, String titleAr, Long departmentId) { }
}
