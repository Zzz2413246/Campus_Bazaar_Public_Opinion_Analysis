package com.nankai.yuqing.controller;

import com.nankai.yuqing.service.AuditService;
import com.nankai.yuqing.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;
    private final AuthService authService;

    public AuditController(AuditService auditService, AuthService authService) {
        this.auditService = auditService;
        this.authService = authService;
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditService.list(action, page, size);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return auditService.summary();
    }

    @GetMapping("/my")
    public Map<String, Object> myLogs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size) {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        return auditService.listForOperator(authService.getNickname(token), page, size);
    }
}
