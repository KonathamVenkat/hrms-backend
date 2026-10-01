package com.hrms.employee;

import com.hrms.employee.config.BusinessTimeZone;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.hrms.employee",
    "com.hrms.auth",
    "com.hrms.leave",
    "com.hrms.attendance", 
    "com.hrms.payroll",
    "com.hrms.dashboard",
    "com.hrms.common"
})
@EnableJpaRepositories(basePackages = {
	    "com.hrms.employee.repository",
	    "com.hrms.auth.repository",       // ← was invisible before
	    "com.hrms.leave.repository",     // ← was invisible before
	    "com.hrms.attendance.repository",
	    "com.hrms.payroll.repository"  
	})
	@EntityScan(basePackages = {
	    "com.hrms.employee.entity",
	    "com.hrms.auth.entity",           // AuthUser, RefreshToken
	    "com.hrms.leave.entity",           // LeaveRequest, LeaveBalance
	    "com.hrms.attendance.entity",
	    "com.hrms.payroll.entity"    
	})
@EnableScheduling
public class EmployeeApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EmployeeApplication.class);
        app.addListeners(new BusinessTimeZone());
        app.run(args);
    }
}
