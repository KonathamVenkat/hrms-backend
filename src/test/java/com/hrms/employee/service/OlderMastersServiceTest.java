package com.hrms.employee.service;

import com.hrms.auth.service.AuditTrail;
import com.hrms.common.exception.BadRequestException;
import com.hrms.employee.config.UploadLimits;
import com.hrms.employee.dto.request.DocumentTypeRequest;
import com.hrms.employee.dto.request.OfficeLocationRequest;
import com.hrms.employee.dto.request.WorkShiftRequest;
import com.hrms.employee.entity.DocumentType;
import com.hrms.employee.entity.OfficeLocation;
import com.hrms.employee.entity.WorkShift;
import com.hrms.employee.repository.DocumentTypeRepository;
import com.hrms.employee.repository.OfficeLocationRepository;
import com.hrms.employee.repository.WorkShiftRepository;
import com.hrms.employee.service.impl.DocumentTypeServiceImpl;
import com.hrms.employee.service.impl.OfficeLocationServiceImpl;
import com.hrms.employee.service.impl.WorkShiftServiceImpl;
import com.hrms.leave.dto.request.HolidayRequest;
import com.hrms.leave.entity.HolidayCalendar;
import com.hrms.leave.repository.HolidayCalendarRepository;
import com.hrms.leave.service.impl.HolidayCalendarServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Document types, office locations, work shifts and holidays: an edit needs the current version (400 when missing, 409
 * when stale) and every write leaves an audit event, like the other masters.
 */
@ExtendWith(MockitoExtension.class)
class OlderMastersServiceTest {

    @Mock DocumentTypeRepository documentTypes;
    @Mock OfficeLocationRepository locations;
    @Mock WorkShiftRepository shifts;
    @Mock HolidayCalendarRepository holidays;
    @Mock AuditTrail audit;

    DocumentTypeServiceImpl documentTypeService;
    OfficeLocationServiceImpl locationService;
    WorkShiftServiceImpl shiftService;
    HolidayCalendarServiceImpl holidayService;

    @BeforeEach
    void setUp() {
        documentTypeService = new DocumentTypeServiceImpl(documentTypes, new UploadLimits(25), audit);
        locationService = new OfficeLocationServiceImpl(locations, audit);
        shiftService = new WorkShiftServiceImpl(shifts, audit);
        holidayService = new HolidayCalendarServiceImpl(holidays, audit);
    }

    // ------------------------------------------------------------------ document types

    private DocumentType passport() {
        return DocumentType.builder().docTypeId(3L).docTypeCode("PASSPORT").docTypeName("Passport").docTypeNameAr("x")
            .version(5L).build();
    }

    private DocumentTypeRequest documentTypeRequest(Long version) {
        return documentTypeRequest("PASSPORT", "Passport", version);
    }

    private DocumentTypeRequest documentTypeRequest(String code, String name, Long version) {
        return DocumentTypeRequest.builder().docTypeCode(code).docTypeName(name).docTypeNameAr("x")
            .category("IDENTITY").version(version).build();
    }

    @Test
    void documentType_updateNeedsTheCurrentVersion() {
        when(documentTypes.findById(3L)).thenReturn(Optional.of(passport()));
        assertThrows(BadRequestException.class, () -> documentTypeService.updateDocumentType(3L, documentTypeRequest(null)));
        assertThrows(OptimisticLockingFailureException.class, () -> documentTypeService.updateDocumentType(3L, documentTypeRequest(4L)));
        verify(documentTypes, never()).saveAndFlush(any());
    }

    @Test
    void documentType_writesAreAudited() {
        when(documentTypes.findById(3L)).thenReturn(Optional.of(passport()));
        when(documentTypes.saveAndFlush(any(DocumentType.class))).thenAnswer(i -> {
            DocumentType d = i.getArgument(0);
            if (d.getDocTypeId() == null) {
                d.setDocTypeId(8L);
            }
            return d;
        });
        when(documentTypes.save(any(DocumentType.class))).thenAnswer(i -> i.getArgument(0));

        documentTypeService.createDocumentType(documentTypeRequest("VISA2", "Visa 2", null));
        documentTypeService.updateDocumentType(3L, documentTypeRequest(5L));
        documentTypeService.deactivateDocumentType(3L);
        documentTypeService.activateDocumentType(3L);

        verify(audit).record(eq("DOCUMENT_TYPE_CREATED"), eq("DOCUMENT_TYPE"), eq(8L), anyString());
        verify(audit).record(eq("DOCUMENT_TYPE_UPDATED"), eq("DOCUMENT_TYPE"), eq(3L), anyString());
        verify(audit).record("DOCUMENT_TYPE_DEACTIVATED", "DOCUMENT_TYPE", 3L, "PASSPORT");
        verify(audit).record("DOCUMENT_TYPE_ACTIVATED", "DOCUMENT_TYPE", 3L, "PASSPORT");
    }

    // ------------------------------------------------------------------ office locations

    private OfficeLocation juba() {
        return OfficeLocation.builder().locationId(4L).locationCode("JUBA_HQ").locationName("Juba HQ").locationNameAr("x")
            .addressLine1("Main road").city("Juba").version(1L).build();
    }

    private OfficeLocationRequest locationRequest(Long version) {
        return locationRequest("JUBA_HQ", "Juba HQ", version);
    }

    private OfficeLocationRequest locationRequest(String code, String name, Long version) {
        return OfficeLocationRequest.builder().locationCode(code).locationName(name).locationNameAr("x")
            .locationType("HEAD_OFFICE").addressLine1("Main road").city("Juba").country("South Sudan").version(version).build();
    }

    @Test
    void location_updateNeedsTheCurrentVersion() {
        when(locations.findById(4L)).thenReturn(Optional.of(juba()));
        assertThrows(BadRequestException.class, () -> locationService.updateLocation(4L, locationRequest(null)));
        assertThrows(OptimisticLockingFailureException.class, () -> locationService.updateLocation(4L, locationRequest(0L)));
        verify(locations, never()).saveAndFlush(any());
    }

    @Test
    void location_writesAreAudited() {
        when(locations.findById(4L)).thenReturn(Optional.of(juba()));
        when(locations.saveAndFlush(any(OfficeLocation.class))).thenAnswer(i -> {
            OfficeLocation l = i.getArgument(0);
            if (l.getLocationId() == null) {
                l.setLocationId(9L);
            }
            return l;
        });
        when(locations.save(any(OfficeLocation.class))).thenAnswer(i -> i.getArgument(0));

        locationService.createLocation(locationRequest("NEW1", "New", null));
        locationService.updateLocation(4L, locationRequest(1L));
        locationService.deactivateLocation(4L);
        locationService.activateLocation(4L);

        verify(audit).record(eq("OFFICE_LOCATION_CREATED"), eq("OFFICE_LOCATION"), eq(9L), anyString());
        verify(audit).record(eq("OFFICE_LOCATION_UPDATED"), eq("OFFICE_LOCATION"), eq(4L), anyString());
        verify(audit).record("OFFICE_LOCATION_DEACTIVATED", "OFFICE_LOCATION", 4L, "JUBA_HQ");
        verify(audit).record("OFFICE_LOCATION_ACTIVATED", "OFFICE_LOCATION", 4L, "JUBA_HQ");
    }

    // ------------------------------------------------------------------ work shifts

    private WorkShift morning() {
        return WorkShift.builder().shiftId(2L).shiftCode("MORNING").shiftName("Morning").shiftNameAr("x")
            .startTime("08:00").endTime("16:00").workingHours(new BigDecimal("7.5")).breakDuration(30)
            .workingDays("MON,TUE,WED,THU,FRI").version(7L).build();
    }

    private WorkShiftRequest shiftRequest(Long version) {
        return shiftRequest("MORNING", "Morning", version);
    }

    private WorkShiftRequest shiftRequest(String code, String name, Long version) {
        return WorkShiftRequest.builder().shiftCode(code).shiftName(name).shiftNameAr("x").shiftType("MORNING")
            .startTime("08:00").endTime("16:00").breakDuration(30).workingHours(new BigDecimal("7.5"))
            .workingDays("MON,TUE,WED,THU,FRI").isOvernight(false).version(version).build();
    }

    @Test
    void shift_updateNeedsTheCurrentVersion() {
        when(shifts.findById(2L)).thenReturn(Optional.of(morning()));
        assertThrows(BadRequestException.class, () -> shiftService.updateShift(2L, shiftRequest(null)));
        assertThrows(OptimisticLockingFailureException.class, () -> shiftService.updateShift(2L, shiftRequest(6L)));
        verify(shifts, never()).saveAndFlush(any());
    }

    @Test
    void shift_writesAreAudited() {
        when(shifts.findById(2L)).thenReturn(Optional.of(morning()));
        when(shifts.saveAndFlush(any(WorkShift.class))).thenAnswer(i -> {
            WorkShift w = i.getArgument(0);
            if (w.getShiftId() == null) {
                w.setShiftId(11L);
            }
            return w;
        });
        when(shifts.save(any(WorkShift.class))).thenAnswer(i -> i.getArgument(0));

        shiftService.createShift(shiftRequest("EARLY", "Early", null));
        shiftService.updateShift(2L, shiftRequest(7L));
        shiftService.deactivateShift(2L);
        shiftService.activateShift(2L);

        verify(audit).record(eq("WORK_SHIFT_CREATED"), eq("WORK_SHIFT"), eq(11L), anyString());
        verify(audit).record(eq("WORK_SHIFT_UPDATED"), eq("WORK_SHIFT"), eq(2L), anyString());
        verify(audit).record("WORK_SHIFT_DEACTIVATED", "WORK_SHIFT", 2L, "MORNING");
        verify(audit).record("WORK_SHIFT_ACTIVATED", "WORK_SHIFT", 2L, "MORNING");
    }

    // ------------------------------------------------------------------ holidays

    private HolidayCalendar newYear() {
        return HolidayCalendar.builder().holidayId(6L).holidayName("New Year").holidayNameAr("x")
            .holidayDate(LocalDate.of(2026, 1, 1)).year(2026).version(2L).build();
    }

    private HolidayRequest holidayRequest(Long version) {
        return holidayRequest("New Year", version);
    }

    private HolidayRequest holidayRequest(String name, Long version) {
        return HolidayRequest.builder().holidayName(name).holidayNameAr("x").holidayDate(LocalDate.of(2026, 1, 1))
            .holidayType("PUBLIC").isRecurring(true).version(version).build();
    }

    @Test
    void holiday_updateNeedsTheCurrentVersion() {
        when(holidays.findById(6L)).thenReturn(Optional.of(newYear()));
        assertThrows(BadRequestException.class, () -> holidayService.updateHoliday(6L, holidayRequest(null)));
        assertThrows(OptimisticLockingFailureException.class, () -> holidayService.updateHoliday(6L, holidayRequest(1L)));
        verify(holidays, never()).saveAndFlush(any());
    }

    @Test
    void holiday_writesAreAudited() {
        when(holidays.findById(6L)).thenReturn(Optional.of(newYear()));
        when(holidays.saveAndFlush(any(HolidayCalendar.class))).thenAnswer(i -> {
            HolidayCalendar h = i.getArgument(0);
            if (h.getHolidayId() == null) {
                h.setHolidayId(12L);
            }
            return h;
        });
        when(holidays.save(any(HolidayCalendar.class))).thenAnswer(i -> i.getArgument(0));

        holidayService.createHoliday(holidayRequest("Peace Day", null));
        holidayService.updateHoliday(6L, holidayRequest(2L));
        holidayService.deactivateHoliday(6L);
        holidayService.activateHoliday(6L);

        verify(audit).record(eq("HOLIDAY_CREATED"), eq("HOLIDAY"), eq(12L), anyString());
        verify(audit).record(eq("HOLIDAY_UPDATED"), eq("HOLIDAY"), eq(6L), anyString());
        verify(audit).record("HOLIDAY_DEACTIVATED", "HOLIDAY", 6L, "New Year");
        verify(audit).record("HOLIDAY_ACTIVATED", "HOLIDAY", 6L, "New Year");
    }
}
