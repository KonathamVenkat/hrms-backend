package com.hrms.employee.mapper;

import com.hrms.employee.dto.response.EmployeeResponse;
import com.hrms.employee.dto.response.EmployeeSummaryResponse;
import com.hrms.employee.entity.Employee;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for Employee entity ↔ DTOs.
 * componentModel = "spring" makes it a Spring bean — injected via constructor.
 */
@Mapper(
	    componentModel = "spring",
	    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
	    unmappedTargetPolicy = ReportingPolicy.WARN
	)
	public interface EmployeeMapper {

	    // ── Entity → EmployeeResponse ─────────────────────────────
	    // ✅ No @Mapping needed for id — MapStruct maps id→id automatically
	    @Mapping(target = "fullNameEn",
	             expression = "java(employee.getFullName())")
	    @Mapping(target = "fullNameAr",
	             expression = "java(employee.getFullNameAr())")
	    @Mapping(target = "gender",
	             expression = "java(employee.getGender() != null ? employee.getGender().name() : null)")
	    @Mapping(target = "bloodGroup",
	             expression = "java(employee.getBloodGroup() != null ? employee.getBloodGroup().getLabel() : null)")
	    @Mapping(target = "maritalStatus",
	             expression = "java(employee.getMaritalStatus() != null ? employee.getMaritalStatus().name() : null)")
	    @Mapping(target = "employmentStatus",
	             expression = "java(employee.getEmploymentStatus() != null ? employee.getEmploymentStatus().name() : null)")
	    @Mapping(target = "employmentType",
	             expression = "java(employee.getEmploymentType() != null ? employee.getEmploymentType().name() : null)")
	    EmployeeResponse toResponse(Employee employee);

	    // ── Entity → EmployeeSummaryResponse ─────────────────────
	    @Mapping(target = "employeeId",       source = "id")   // ✅ SummaryResponse has employeeId
	    // Department and designation come from the current job record, not from the employee row.
	    @Mapping(target = "departmentId",     ignore = true)
	    @Mapping(target = "departmentName",   ignore = true)
	    @Mapping(target = "departmentCode",   ignore = true)
	    @Mapping(target = "designationId",    ignore = true)
	    @Mapping(target = "designationTitle", ignore = true)
	    @Mapping(target = "gradeLevel",       ignore = true)
	    @Mapping(target = "fullNameEn",
	             expression = "java(employee.getFullName())")
	    @Mapping(target = "gender",
	             expression = "java(employee.getGender() != null ? employee.getGender().name() : null)")
	    @Mapping(target = "employmentStatus",
	             expression = "java(employee.getEmploymentStatus() != null ? employee.getEmploymentStatus().name() : null)")
	    @Mapping(target = "employmentType",
	             expression = "java(employee.getEmploymentType() != null ? employee.getEmploymentType().name() : null)")
	    EmployeeSummaryResponse toSummaryResponse(Employee employee);

	    List<EmployeeSummaryResponse> toSummaryResponseList(List<Employee> employees);

	}