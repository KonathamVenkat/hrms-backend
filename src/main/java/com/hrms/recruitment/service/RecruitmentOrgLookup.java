package com.hrms.recruitment.service;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Designation;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.DesignationRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.recruitment.repository.DepartmentPeopleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The only place the recruitment module reaches into the employee module's data: departments, designations,
 * locations and employees. Keeping it in one class keeps the module easy to pull out into its own service later.
 */
@Component
@RequiredArgsConstructor
public class RecruitmentOrgLookup {

    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final OfficeLocationRepository locations;
    private final EmployeeRepository employees;
    private final DepartmentPeopleRepository people;

    /** The department must exist and be active. */
    public Department activeDepartment(Long id) {
        Department d = departments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department", "id", id));
        if (!Boolean.TRUE.equals(d.getIsActive())) {
            throw new BusinessRuleException("DEPARTMENT_INACTIVE", "Department " + d.getCode() + " is inactive.");
        }
        return d;
    }

    /** The designation must exist and be active; when it belongs to a department, that must be {@code departmentId}. */
    public Designation activeDesignation(Long id, Long departmentId) {
        Designation d = designations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Designation", "id", id));
        if (!Boolean.TRUE.equals(d.getIsActive())) {
            throw new BusinessRuleException("DESIGNATION_INACTIVE", "Designation " + d.getCode() + " is inactive.");
        }
        if (d.getDepartmentId() != null && !d.getDepartmentId().equals(departmentId)) {
            throw new BusinessRuleException("DESIGNATION_DEPARTMENT_MISMATCH",
                "Designation " + d.getCode() + " belongs to a different department.");
        }
        return d;
    }

    public void requireActiveLocation(Long id) {
        OfficeLocation l = locations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Office location", "id", id));
        if (!Integer.valueOf(1).equals(l.getIsActive())) {
            throw new BusinessRuleException("LOCATION_INACTIVE", "Location " + l.getLocationCode() + " is inactive.");
        }
    }

    public void requireActiveEmployee(Long id) {
        Employee e = employees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee", "id", id));
        if (!Boolean.TRUE.equals(e.getIsActive())) {
            throw new BusinessRuleException("EMPLOYEE_INACTIVE", "Employee " + e.getEmployeeCode() + " is inactive.");
        }
    }

    public void requireEmployeeExists(Long id) {
        if (!employees.existsById(id)) {
            throw new ResourceNotFoundException("Employee", "id", id);
        }
    }

    public boolean isDepartmentHead(Long departmentId, Long employeeId) {
        return employeeId != null && departments.findById(departmentId)
            .map(d -> Objects.equals(d.getHeadEmployeeId(), employeeId)).orElse(false);
    }

    /** Departments are a small master table, so filtering in memory is cheaper than another repository method. */
    public List<Long> departmentIdsHeadedBy(Long employeeId) {
        return departments.findAll().stream()
            .filter(d -> Objects.equals(d.getHeadEmployeeId(), employeeId))
            .map(Department::getDeptId).toList();
    }

    /** Active departments the person may raise a requisition for: all of them for HR, the ones they head otherwise. */
    public List<Department> departmentsRaisableBy(Long employeeId, boolean hrStaff) {
        if (!hrStaff && employeeId == null) return List.of();
        return departments.findByIsActiveOrderByNameAsc(true).stream()
            .filter(d -> hrStaff || Objects.equals(d.getHeadEmployeeId(), employeeId))
            .toList();
    }

    public List<Designation> activeDesignations() {
        return designations.findByIsActiveOrderByTitleAsc(true);
    }

    public List<OfficeLocation> activeLocations() {
        return locations.findByIsActiveOrderBySortOrderAsc(1);
    }

    /** Active employees whose current job is in the department, by name. */
    public List<Employee> activeEmployeesOf(Long departmentId) {
        return employees.findAllById(people.findCurrentEmployeeIds(departmentId)).stream()
            .filter(e -> Boolean.TRUE.equals(e.getIsActive()))
            .sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    public Map<Long, String> departmentNames(Collection<Long> ids) {
        return names(departments.findAllById(ids), Department::getDeptId, Department::getName);
    }

    public Map<Long, String> designationTitles(Collection<Long> ids) {
        return names(designations.findAllById(ids), Designation::getDesigId, Designation::getTitle);
    }

    public Map<Long, String> employeeNames(Collection<Long> ids) {
        return names(employees.findAllById(ids), Employee::getId, Employee::getFullName);
    }

    private static <T> Map<Long, String> names(List<T> rows, Function<T, Long> id, Function<T, String> name) {
        return rows.stream().collect(Collectors.toMap(id, name, (a, b) -> a));
    }
}
