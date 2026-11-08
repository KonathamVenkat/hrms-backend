package com.hrms.leave.service;

import com.hrms.leave.dto.request.HolidayRequest;
import com.hrms.leave.dto.response.HolidayResponse;

import java.util.List;

public interface HolidayCalendarService {

    List<HolidayResponse> getHolidaysByYear(Integer year);
    List<HolidayResponse> getActiveHolidaysByYear(Integer year);
    HolidayResponse       getHolidayById(Long id);
    HolidayResponse       createHoliday(HolidayRequest request);
    HolidayResponse       updateHoliday(Long id, HolidayRequest request);
    void                  deactivateHoliday(Long id);
    void                  activateHoliday(Long id);
}