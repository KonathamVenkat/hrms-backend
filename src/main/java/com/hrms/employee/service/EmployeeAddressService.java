package com.hrms.employee.service;

import com.hrms.employee.dto.request.EmployeeAddressRequest;
import com.hrms.employee.dto.response.EmployeeAddressResponse;
import java.util.List;

public interface EmployeeAddressService {

    List<EmployeeAddressResponse> getAddresses(Long employeeId);
    EmployeeAddressResponse       getAddressById(Long employeeId, Long addressId);
    EmployeeAddressResponse       addAddress(Long employeeId, EmployeeAddressRequest request);
    EmployeeAddressResponse       updateAddress(Long employeeId, Long addressId, EmployeeAddressRequest request);
    void                          setPrimary(Long employeeId, Long addressId);
    void                          deactivateAddress(Long employeeId, Long addressId);
}
