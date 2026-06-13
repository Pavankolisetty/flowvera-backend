package com.workly.controller;

import com.workly.dto.ErrorResponse;
import com.workly.dto.LeaveBalanceResponse;
import com.workly.dto.LeaveEmployeeOptionDto;
import com.workly.dto.LeaveRequestCreateRequest;
import com.workly.dto.LeaveRequestResponse;
import com.workly.dto.MessageResponse;
import com.workly.service.LeaveRequestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    @GetMapping("/api/employee/leave/summary")
    public ResponseEntity<LeaveBalanceResponse> getBalance(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.getBalance(auth.getName()));
    }

    @GetMapping("/api/employee/leave/requests")
    public ResponseEntity<List<LeaveRequestResponse>> getMyRequests(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.getMyRequests(auth.getName()));
    }

    @GetMapping("/api/employee/leave/eligible-dependencies")
    public ResponseEntity<List<LeaveEmployeeOptionDto>> getEligibleDependencies(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.getEligibleDependencies(auth.getName()));
    }

    @PostMapping("/api/employee/leave/requests")
    public ResponseEntity<?> createRequest(
            @RequestBody LeaveRequestCreateRequest request,
            Authentication auth) {
        try {
            return ResponseEntity.ok(leaveRequestService.createRequest(auth.getName(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @PutMapping("/api/employee/leave/notifications/read")
    public ResponseEntity<List<LeaveRequestResponse>> markNotificationsRead(Authentication auth) {
        return ResponseEntity.ok(leaveRequestService.markNotificationsRead(auth.getName()));
    }

    @GetMapping(value = "/api/leave/action/approve", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> approve(@RequestParam String token) {
        return renderActionResult(() -> leaveRequestService.approveByToken(token));
    }

    @GetMapping(value = "/api/leave/action/reject", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> reject(@RequestParam String token) {
        return renderActionResult(() -> leaveRequestService.rejectByToken(token));
    }

    private ResponseEntity<String> renderActionResult(Action action) {
        try {
            String message = action.run();
            return ResponseEntity.ok(renderHtml("Request updated", message, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(renderHtml("Unable to update request", e.getMessage(), false));
        }
    }

    private String renderHtml(String title, String message, boolean success) {
        String color = success ? "#166534" : "#991b1b";
        return """
            <!doctype html>
            <html>
              <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title></head>
              <body style="margin:0;font-family:Arial,Helvetica,sans-serif;background:#f3f4f6;color:#111827;">
                <main style="min-height:100vh;display:grid;place-items:center;padding:24px;">
                  <section style="max-width:520px;background:#fff;border:1px solid #e5e7eb;border-radius:18px;padding:30px;box-shadow:0 22px 48px rgba(15,23,42,.12);">
                    <div style="width:54px;height:54px;border-radius:16px;background:%s;color:#fff;display:grid;place-items:center;font-weight:800;margin-bottom:18px;">%s</div>
                    <h1 style="font-size:24px;margin:0 0 10px;">%s</h1>
                    <p style="font-size:16px;line-height:1.6;margin:0;">%s</p>
                  </section>
                </main>
              </body>
            </html>
            """.formatted(title, color, success ? "OK" : "!", title, message);
    }

    @FunctionalInterface
    private interface Action {
        String run();
    }
}
