package com.hrms.employee.service;

import com.hrms.employee.dto.response.EmployeeResponse;
import org.springframework.web.multipart.MultipartFile;

/** Upload, serve and remove an employee's profile photo (stored in the database). */
public interface EmployeePhotoService {

    /** An uploaded photo: the image bytes and their (verified) media type. */
    record Photo(byte[] content, String contentType) {}

    /**
     * Stores {@code file} as the employee's photo (JPEG, PNG or WebP, at most 2 MB) and points the
     * employee's profile photo URL at it.
     */
    EmployeeResponse upload(Long employeeId, MultipartFile file);

    /** The stored photo; throws ResourceNotFoundException when the employee has none. */
    Photo get(Long employeeId);

    /** Removes the uploaded photo and clears the employee's profile photo URL. */
    EmployeeResponse remove(Long employeeId);
}
