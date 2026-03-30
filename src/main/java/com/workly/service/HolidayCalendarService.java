package com.workly.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

public interface HolidayCalendarService {
    Map<LocalDate, String> getNationalHolidays(YearMonth month);
}
