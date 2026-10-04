package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.config.UploadHeader;
import com.hrms.employee.dto.response.EmployeeResponse;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeePhoto;
import com.hrms.employee.mapper.EmployeeMapper;
import com.hrms.employee.repository.EmployeePhotoRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.EmployeePhotoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeePhotoServiceImpl implements EmployeePhotoService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final String JPEG = "image/jpeg";
    private static final String PNG  = "image/png";
    private static final String WEBP = "image/webp";

    private final EmployeeRepository      employeeRepository;
    private final EmployeePhotoRepository photoRepository;
    private final EmployeeMapper          employeeMapper;

    @Override
    @Transactional
    public EmployeeResponse upload(Long employeeId, MultipartFile file) {
        Employee employee = findActiveEmployee(employeeId);

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("PHOTO_REQUIRED", "Choose a photo to upload.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessRuleException("PHOTO_TOO_LARGE", "The photo must be 2 MB or smaller.");
        }
        // The declared type and file name are client-controlled; only the leading bytes are trusted.
        String contentType = detectType(UploadHeader.read(file));
        if (contentType == null) {
            throw new BusinessRuleException("PHOTO_TYPE_NOT_ALLOWED",
                "The photo must be a JPG, PNG or WebP image.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded photo: {}", e.getMessage());
            throw new BusinessRuleException("FILE_SAVE_ERROR", "Failed to save the photo. Please try again.");
        }

        EmployeePhoto photo = photoRepository.findById(employeeId).orElseGet(EmployeePhoto::new);
        photo.setEmployeeId(employeeId);
        photo.setContent(bytes);
        photo.setContentType(contentType);
        photo.setUpdatedAt(LocalDateTime.now());
        photoRepository.save(photo);

        employee.setProfilePhotoUrl(photoUrl(employeeId));
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    @Override
    public Photo get(Long employeeId) {
        EmployeePhoto photo = photoRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("EmployeePhoto", "employeeId", employeeId));
        return new Photo(photo.getContent(), photo.getContentType());
    }

    @Override
    @Transactional
    public EmployeeResponse remove(Long employeeId) {
        Employee employee = findActiveEmployee(employeeId);
        photoRepository.deleteById(employeeId);
        employee.setProfilePhotoUrl(null);
        return employeeMapper.toResponse(employeeRepository.save(employee));
    }

    /** App-relative path the frontend recognises as "load this with the signed-in session". */
    static String photoUrl(Long employeeId) {
        return "/api/v1/employees/" + employeeId + "/photo";
    }

    private Employee findActiveEmployee(Long employeeId) {
        return employeeRepository.findByIdAndIsActive(employeeId, true)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", "id", employeeId));
    }

    /** The media type for the image formats we accept, judged by the file signature; else null. */
    static String detectType(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
            return PNG;
        }
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return WEBP;
        }
        return null;
    }
}
