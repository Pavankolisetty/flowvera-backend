package com.workly.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleHolidayCalendarService implements HolidayCalendarService {

    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();

    private final Map<YearMonth, Map<LocalDate, String>> cache = new ConcurrentHashMap<>();

    @Value("${attendance.holiday.google-calendar-id:en.indian#holiday@group.v.calendar.google.com}")
    private String googleCalendarId;

    @Value("${attendance.holiday.google-calendar-enabled:true}")
    private boolean holidayCalendarEnabled;

    @Override
    public Map<LocalDate, String> getNationalHolidays(YearMonth month) {
        if (!holidayCalendarEnabled) {
            return Map.of();
        }

        return cache.computeIfAbsent(month, this::fetchMonth);
    }

    private Map<LocalDate, String> fetchMonth(YearMonth month) {
        try {
            String encodedId = URLEncoder.encode(googleCalendarId, StandardCharsets.UTF_8);
            URI uri = URI.create("https://calendar.google.com/calendar/ical/" + encodedId + "/public/basic.ics");
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Map.of();
            }

            return parseIcs(response.body(), month);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return Map.of();
        } catch (IOException ignored) {
            return Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<LocalDate, String> parseIcs(String rawIcs, YearMonth month) {
        List<String> lines = unfoldLines(rawIcs);
        Map<LocalDate, String> holidays = new LinkedHashMap<>();
        Map<String, String> event = null;

        for (String line : lines) {
            if ("BEGIN:VEVENT".equals(line)) {
                event = new HashMap<>();
                continue;
            }

            if ("END:VEVENT".equals(line)) {
                appendEvent(holidays, event, month);
                event = null;
                continue;
            }

            if (event == null) {
                continue;
            }

            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }

            String key = line.substring(0, separator);
            String value = line.substring(separator + 1);
            event.put(key, value);
        }

        return holidays;
    }

    private void appendEvent(Map<LocalDate, String> holidays, Map<String, String> event, YearMonth month) {
        if (event == null) {
            return;
        }

        String summary = event.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("SUMMARY"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);

        String startValue = event.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("DTSTART"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);

        String endValue = event.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith("DTEND"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);

        if (summary == null || startValue == null) {
            return;
        }

        LocalDate startDate = parseDate(startValue);
        if (startDate == null) {
            return;
        }

        LocalDate exclusiveEnd = parseDate(endValue);
        if (exclusiveEnd == null || !exclusiveEnd.isAfter(startDate)) {
            exclusiveEnd = startDate.plusDays(1);
        }

        LocalDate cursor = startDate;
        while (cursor.isBefore(exclusiveEnd)) {
            if (YearMonth.from(cursor).equals(month)) {
                holidays.putIfAbsent(cursor, summary);
            }
            cursor = cursor.plusDays(1);
        }
    }

    private LocalDate parseDate(String value) {
        String compactValue = value.length() >= 8 ? value.substring(0, 8) : value;
        if (!compactValue.matches("\\d{8}")) {
            return null;
        }
        return LocalDate.parse(compactValue, ICS_DATE);
    }

    private List<String> unfoldLines(String rawIcs) {
        String normalized = rawIcs.replace("\r\n", "\n");
        String[] splitLines = normalized.split("\n");
        List<String> unfolded = new ArrayList<>();

        for (String line : splitLines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !unfolded.isEmpty()) {
                int lastIndex = unfolded.size() - 1;
                unfolded.set(lastIndex, unfolded.get(lastIndex) + line.substring(1));
            } else {
                unfolded.add(line);
            }
        }

        return unfolded;
    }
}
