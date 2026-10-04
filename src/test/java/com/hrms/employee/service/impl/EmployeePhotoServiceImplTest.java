package com.hrms.employee.service.impl;

import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.entity.Employee;
import com.hrms.employee.entity.EmployeePhoto;
import com.hrms.employee.mapper.EmployeeMapper;
import com.hrms.employee.repository.EmployeePhotoRepository;
import com.hrms.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeePhotoServiceImplTest {

    private static final byte[] PNG  = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    EmployeeRepository      employees = mock(EmployeeRepository.class);
    EmployeePhotoRepository photos    = mock(EmployeePhotoRepository.class);
    EmployeeMapper          mapper    = mock(EmployeeMapper.class);
    EmployeePhotoServiceImpl service;
    Employee employee;

    @BeforeEach
    void setUp() {
        service = new EmployeePhotoServiceImpl(employees, photos, mapper);
        employee = new Employee();
        when(employees.findByIdAndIsActive(5L, true)).thenReturn(Optional.of(employee));
        when(employees.save(any())).thenAnswer(i -> i.getArgument(0));
        when(photos.findById(5L)).thenReturn(Optional.empty());
    }

    @Test
    void detectsTheSupportedImageFormatsBySignature() {
        assertEquals("image/png", EmployeePhotoServiceImpl.detectType(PNG));
        assertEquals("image/jpeg", EmployeePhotoServiceImpl.detectType(JPEG));
        assertEquals("image/webp", EmployeePhotoServiceImpl.detectType(WEBP));
        assertNull(EmployeePhotoServiceImpl.detectType("<svg></svg>".getBytes()));
        assertNull(EmployeePhotoServiceImpl.detectType(new byte[0]));
    }

    @Test
    void storesThePhotoAndPointsTheEmployeeAtIt() {
        service.upload(5L, new MockMultipartFile("file", "me.png", "text/plain", PNG));

        ArgumentCaptor<EmployeePhoto> saved = ArgumentCaptor.forClass(EmployeePhoto.class);
        verify(photos).save(saved.capture());
        assertEquals("image/png", saved.getValue().getContentType()); // from the bytes, not the header
        assertArrayEquals(PNG, saved.getValue().getContent());
        assertTrue(employee.getProfilePhotoUrl().matches("/api/v1/employees/5/photo\\?v=\\d+"));
    }

    @Test
    void rejectsAFileThatIsNotAnAllowedImage() {
        var svg = new MockMultipartFile("file", "x.png", "image/png", "<svg></svg>".getBytes());
        var ex = assertThrows(BusinessRuleException.class, () -> service.upload(5L, svg));
        assertEquals("PHOTO_TYPE_NOT_ALLOWED", ex.getRuleCode());
        verify(photos, never()).save(any());
    }

    @Test
    void rejectsAPhotoOverTwoMegabytes() {
        byte[] big = Arrays.copyOf(PNG, (int) EmployeePhotoServiceImpl.MAX_BYTES + 1);
        var ex = assertThrows(BusinessRuleException.class,
            () -> service.upload(5L, new MockMultipartFile("file", "big.png", "image/png", big)));
        assertEquals("PHOTO_TOO_LARGE", ex.getRuleCode());
    }

    @Test
    void rejectsAnEmptyUpload() {
        var ex = assertThrows(BusinessRuleException.class,
            () -> service.upload(5L, new MockMultipartFile("file", "a.png", "image/png", new byte[0])));
        assertEquals("PHOTO_REQUIRED", ex.getRuleCode());
    }

    @Test
    void anUnknownOrDeactivatedEmployeeIsNotFound() {
        when(employees.findByIdAndIsActive(9L, true)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> service.upload(9L, new MockMultipartFile("file", "a.png", "image/png", PNG)));
    }

    @Test
    void removingDeletesTheImageAndClearsTheUrl() {
        employee.setProfilePhotoUrl("/api/v1/employees/5/photo");
        service.remove(5L);
        verify(photos).deleteById(5L);
        assertNull(employee.getProfilePhotoUrl());
    }

    @Test
    void servingAnEmployeeWithoutAPhotoIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(5L));
    }
}
