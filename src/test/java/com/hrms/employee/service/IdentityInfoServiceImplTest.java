package com.hrms.employee.service;

import com.hrms.auth.security.EmployeeAccessGuard;
import com.hrms.common.exception.BusinessRuleException;
import com.hrms.common.exception.ResourceNotFoundException;
import com.hrms.employee.dto.request.IdentityInfoRequest;
import com.hrms.employee.dto.response.IdentityInfoResponse;
import com.hrms.employee.entity.EmployeeIdentityInfo;
import com.hrms.employee.repository.EmployeeIdentityInfoRepository;
import com.hrms.employee.repository.EmployeeRepository;
import com.hrms.employee.service.impl.IdentityInfoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdentityInfoServiceImplTest {

    @Mock EmployeeIdentityInfoRepository identityRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock EmployeeAccessGuard accessGuard;

    IdentityInfoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new IdentityInfoServiceImpl(identityRepository, employeeRepository, accessGuard);
    }

    private EmployeeIdentityInfo record() {
        return EmployeeIdentityInfo.builder()
            .employeeIdentityId(1L).employeeId(5L)
            .nationalId("123456789").passportNumber("P1234567").taxId("TAX-99887766")
            .socialSecurityNumber("SSN-4455").drivingLicenseNumber("DL9876")
            .visaNumber("V-5544332").visaType("Work").workPermitNumber("WP-778899")
            .biometricId("BIO12").visaExpiryDate(LocalDate.now().plusDays(10))
            .build();
    }

    // ── Masking ─────────────────────────────────────────────

    @Test
    void maskKeepsOnlyTheLastFourCharacters() {
        assertEquals("*****6789", IdentityInfoServiceImpl.mask("123456789"));
        assertEquals("****", IdentityInfoServiceImpl.mask("1234"));   // short values fully masked
        assertEquals("**", IdentityInfoServiceImpl.mask("12"));
        assertNull(IdentityInfoServiceImpl.mask(null));
        assertEquals(" ", IdentityInfoServiceImpl.mask(" "));
    }

    @Test
    void viewerWithoutPermissionGetsMaskedNumbersAndAFlag() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(accessGuard.canViewUnmasked(5L)).thenReturn(false);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.of(record()));

        IdentityInfoResponse r = service.getIdentityInfo(5L);

        assertTrue(r.getMasked());
        assertEquals("*****6789", r.getNationalId());
        assertEquals("****4567", r.getPassportNumber());
        assertEquals("********7766", r.getTaxId());
        assertTrue(r.getSocialSecurityNumber().endsWith("4455") && r.getSocialSecurityNumber().startsWith("*"));
        assertTrue(r.getDrivingLicenseNumber().endsWith("9876") && r.getDrivingLicenseNumber().startsWith("*"));
        assertTrue(r.getVisaNumber().endsWith("4332") && r.getVisaNumber().startsWith("*"));
        assertTrue(r.getWorkPermitNumber().endsWith("8899") && r.getWorkPermitNumber().startsWith("*"));
        assertEquals("*IO12", r.getBiometricId());
    }

    @Test
    void maskedViewStillShowsVisaTypeAndExpiryInformation() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(accessGuard.canViewUnmasked(5L)).thenReturn(false);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.of(record()));

        IdentityInfoResponse r = service.getIdentityInfo(5L);

        assertEquals("Work", r.getVisaType());
        assertNotNull(r.getVisaExpiryDate());
        assertTrue(r.getVisaExpiringSoon());
    }

    @Test
    void hrAdminOrTheEmployeeGetsRealValues() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(accessGuard.canViewUnmasked(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.of(record()));

        IdentityInfoResponse r = service.getIdentityInfo(5L);

        assertFalse(r.getMasked());
        assertEquals("123456789", r.getNationalId());
        assertEquals("P1234567", r.getPassportNumber());
        assertEquals("BIO12", r.getBiometricId());
    }

    @Test
    void employeeWithNoRecordGetsAnEmptyShell() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(accessGuard.canViewUnmasked(5L)).thenReturn(false);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

        IdentityInfoResponse r = service.getIdentityInfo(5L);

        assertEquals(5L, r.getEmployeeId());
        assertNull(r.getNationalId());
    }

    @Test
    void unknownEmployeeIsNotFound() {
        when(employeeRepository.existsById(5L)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.getIdentityInfo(5L));
        verifyNoInteractions(identityRepository);
    }

    // ── Save ────────────────────────────────────────────────


    @Test
    void savingFromAStaleScreenIsRefused() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        EmployeeIdentityInfo current = record();
        current.setVersion(4L);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.of(current));
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setNationalId("123456789");
        req.setVersion(3L);

        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
            () -> service.saveIdentityInfo(5L, req));
        verify(identityRepository, never()).save(any());
    }
    @Test
    void saveReturnsRealValuesNotMaskedOnes() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(identityRepository.save(any(EmployeeIdentityInfo.class))).thenAnswer(i -> i.getArgument(0));
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setNationalId("  123456789 ");

        IdentityInfoResponse r = service.saveIdentityInfo(5L, req);

        assertFalse(r.getMasked());
        assertEquals("123456789", r.getNationalId());   // trimmed, not masked
    }

    @Test
    void duplicateNationalIdOnAnotherEmployeeIsRejected() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.existsByNationalIdAndEmployeeIdNot("123456789", 5L)).thenReturn(true);
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setNationalId("123456789");

        BusinessRuleException ex =
            assertThrows(BusinessRuleException.class, () -> service.saveIdentityInfo(5L, req));
        assertEquals("DUPLICATE_NATIONAL_ID", ex.getRuleCode());
        verify(identityRepository, never()).save(any());
    }

    @Test
    void visaExpiryBeforeIssueIsRejected() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setVisaIssueDate(LocalDate.of(2026, 5, 1));
        req.setVisaExpiryDate(LocalDate.of(2026, 4, 1));

        BusinessRuleException ex =
            assertThrows(BusinessRuleException.class, () -> service.saveIdentityInfo(5L, req));
        assertEquals("INVALID_VISA_DATES", ex.getRuleCode());
    }

    // ── National-ID details and residence (V5 columns now mapped, V17 residence valid-until) ──

    @Test
    void nationalIdDetailsAndResidenceAreStoredTrimmed_andReturnedToTheAdmin() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(identityRepository.save(any(EmployeeIdentityInfo.class))).thenAnswer(i -> i.getArgument(0));
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setNationalIdType(" By birth ");
        req.setNationalIdIssueDate(LocalDate.of(1986, 9, 22));
        req.setNationalIdIssuePlace(" Omdurman ");
        req.setResidenceDate(LocalDate.of(2020, 1, 1));
        req.setResidencePlace(" Wad Balal ");
        req.setResidenceValidUntil(LocalDate.of(2030, 1, 1));

        IdentityInfoResponse r = service.saveIdentityInfo(5L, req);

        assertEquals("By birth", r.getNationalIdType());
        assertEquals(LocalDate.of(1986, 9, 22), r.getNationalIdIssueDate());
        assertEquals("Omdurman", r.getNationalIdIssuePlace());
        assertEquals("Wad Balal", r.getResidencePlace());
        assertEquals(LocalDate.of(2030, 1, 1), r.getResidenceValidUntil());
    }

    @Test
    void updatingAnExistingRecordOverwritesTheResidenceFields_andEmptyClearsThem() {
        EmployeeIdentityInfo existing = EmployeeIdentityInfo.builder().employeeId(5L).version(3L)
            .nationalIdType("Old").residencePlace("Old place").residenceValidUntil(LocalDate.of(2025, 1, 1)).build();
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.of(existing));
        when(identityRepository.save(any(EmployeeIdentityInfo.class))).thenAnswer(i -> i.getArgument(0));
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setVersion(3L);
        req.setNationalIdType(" New ");
        req.setResidencePlace("  ");

        service.saveIdentityInfo(5L, req);

        assertEquals("New", existing.getNationalIdType());
        assertNull(existing.getResidencePlace());
        assertNull(existing.getResidenceValidUntil());
    }

    @Test
    void residenceValidUntilBeforeTheResidenceDateIsRejected_butTheSameDayIsFine() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        IdentityInfoRequest backwards = new IdentityInfoRequest();
        backwards.setResidenceDate(LocalDate.of(2026, 5, 2));
        backwards.setResidenceValidUntil(LocalDate.of(2026, 5, 1));

        BusinessRuleException ex =
            assertThrows(BusinessRuleException.class, () -> service.saveIdentityInfo(5L, backwards));
        assertEquals("INVALID_RESIDENCE_DATES", ex.getRuleCode());
        verify(identityRepository, never()).save(any());

        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(identityRepository.save(any(EmployeeIdentityInfo.class))).thenAnswer(i -> i.getArgument(0));
        IdentityInfoRequest sameDay = new IdentityInfoRequest();
        sameDay.setResidenceDate(LocalDate.of(2026, 5, 1));
        sameDay.setResidenceValidUntil(LocalDate.of(2026, 5, 1));
        assertDoesNotThrow(() -> service.saveIdentityInfo(5L, sameDay));
    }

    @Test
    void aNationalIdIssuedInTheFutureIsRejected() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setNationalIdIssueDate(LocalDate.now().plusDays(1));

        BusinessRuleException ex =
            assertThrows(BusinessRuleException.class, () -> service.saveIdentityInfo(5L, req));
        assertEquals("INVALID_NATIONAL_ID_DATES", ex.getRuleCode());
    }

    // ── An empty first save is refused ──────────────────────

    @Test
    void aFirstSaveWithNothingInItIsRefused_andNothingIsStored() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

        BusinessRuleException ex =
            assertThrows(BusinessRuleException.class, () -> service.saveIdentityInfo(5L, new IdentityInfoRequest()));

        assertEquals("EMPTY_IDENTITY", ex.getRuleCode());
        verify(identityRepository, never()).save(any());
    }

    @Test
    void aFirstSaveWithOnlyBlankTextIsRefusedToo() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setNationalId("   ");
        req.setVisaType("");

        assertEquals("EMPTY_IDENTITY",
            assertThrows(BusinessRuleException.class, () -> service.saveIdentityInfo(5L, req)).getRuleCode());
    }

    @Test
    void aFirstSaveWithOnlyADateIsAccepted() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(identityRepository.save(any(EmployeeIdentityInfo.class))).thenAnswer(i -> i.getArgument(0));
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setWorkPermitExpiry(LocalDate.of(2030, 1, 1));

        assertDoesNotThrow(() -> service.saveIdentityInfo(5L, req));
    }

    @Test
    void clearingEverythingOnASavedRecordStaysAllowed() {
        EmployeeIdentityInfo existing = EmployeeIdentityInfo.builder().employeeId(5L).version(2L).nationalId("123456789").build();
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(identityRepository.findByEmployeeId(5L)).thenReturn(Optional.of(existing));
        when(identityRepository.save(any(EmployeeIdentityInfo.class))).thenAnswer(i -> i.getArgument(0));
        IdentityInfoRequest req = new IdentityInfoRequest();
        req.setVersion(2L);

        assertDoesNotThrow(() -> service.saveIdentityInfo(5L, req));
        assertNull(existing.getNationalId());
    }
}
