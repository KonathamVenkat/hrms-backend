package com.hrms.employee.service;

import com.hrms.employee.entity.Employee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmployeeFullNameTest {

    private Employee.EmployeeBuilder base() {
        return Employee.builder().firstName("Ahmed").middleName("Khair").lastName("Mohamed")
            .firstNameAr("أحمد").middleNameAr("خير").lastNameAr("محمد");
    }

    @Test
    void fourthNameComesLast_inEnglishAndArabic() {
        Employee e = base().fourthName("Ibrahim").fourthNameAr("إبراهيم").build();

        assertEquals("Ahmed Khair Mohamed Ibrahim", e.getFullName());
        assertEquals("أحمد خير محمد إبراهيم", e.getFullNameAr());
    }

    @Test
    void withoutAFourthName_theNameIsAsBefore() {
        Employee blank = base().fourthName("  ").build();

        assertEquals("Ahmed Khair Mohamed", blank.getFullName());
        assertEquals("أحمد خير محمد", base().build().getFullNameAr());
    }
}
