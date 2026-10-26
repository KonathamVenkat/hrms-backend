package com.hrms.employee;

import com.hrms.common.enums.EmploymentType;
import com.hrms.common.enums.Gender;
import com.hrms.employee.entity.Department;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.repository.DepartmentRepository;
import com.hrms.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** The department secretary column (V10) is mapped: it round-trips and findBySecretaryEmployeeId finds it. */
@Transactional
class DepartmentSecretaryDbTest extends DatabaseTest {

    @Autowired DepartmentRepository departmentRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EntityManager em;

    @Test
    void secretaryEmployeeIdIsSavedReloadedAndSearchable() {
        Long secretary = secretaryEmployee();
        Long deptId = departmentRepository.saveAndFlush(Department.builder().code("DSDB").name("Secretary test")
            .nameAr("x").isActive(true).secretaryEmployeeId(secretary).build()).getDeptId();
        em.clear();

        Department reloaded = departmentRepository.findById(deptId).orElseThrow();

        assertEquals(secretary, reloaded.getSecretaryEmployeeId());
        assertEquals(1, departmentRepository.findBySecretaryEmployeeId(secretary).size());
        assertTrue(departmentRepository.findBySecretaryEmployeeId(-1L).isEmpty());
    }

    private Long secretaryEmployee() {
        return employeeRepository.saveAndFlush(Employee.builder()
            .firstName("Sec").firstNameAr("ع").lastName("Retary").lastNameAr("ل")
            .dateOfBirth(LocalDate.of(1988, 3, 2)).gender(Gender.FEMALE)
            .personalEmail("dsdb-p@example.test").workEmail("dsdb-w@example.test")
            .hireDate(LocalDate.of(2024, 1, 15)).employmentType(EmploymentType.FULL_TIME).build()).getId();
    }
}
