package com.nankai.yuqing.service;

import com.nankai.yuqing.model.AuditLog;
import com.nankai.yuqing.repository.AuditLogRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void sanitizeLegacySources() {
        var changed = repository.findAll().stream().filter(log -> {
            String source = log.getIpAddress();
            if (source == null || source.equals("本机") || source.equals("外部来源")) return false;
            log.setIpAddress(source.equals("127.0.0.1") || source.equals("::1")
                || source.equals("0:0:0:0:0:0:0:1") ? "本机" : "外部来源");
            return true;
        }).toList();
        if (!changed.isEmpty()) repository.saveAll(changed);
    }

    public void record(String operator, String role, String actionCode, String actionName,
                       String targetType, String targetId, String summary, String status, String ip) {
        AuditLog log = new AuditLog();
        log.setOperatorName(blank(operator, "未知用户"));
        log.setOperatorRole(blank(role, "UNKNOWN"));
        log.setActionCode(actionCode);
        log.setActionName(actionName);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setSummary(summary);
        log.setStatus(status);
        log.setIpAddress(ip);
        log.setCreatedAt(LocalDateTime.now());
        repository.save(log);
    }

    public Map<String, Object> list(String action, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Page<AuditLog> result = action == null || action.isBlank()
            ? repository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage - 1, safeSize))
            : repository.findByActionCodeOrderByCreatedAtDesc(action.trim(), PageRequest.of(safePage - 1, safeSize));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", safePage);
        response.put("size", safeSize);
        response.put("total", result.getTotalElements());
        response.put("data", result.getContent().stream().map(this::toMap).toList());
        return response;
    }

    public Map<String, Object> summary() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return Map.of(
            "last24Hours", repository.countByCreatedAtAfter(since),
            "failedLast24Hours", repository.countByStatusAndCreatedAtAfter("失败", since),
            "total", repository.count());
    }

    public Map<String, Object> listForOperator(String operator, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(50, size));
        Page<AuditLog> result = repository.findByOperatorNameOrderByCreatedAtDesc(
            operator, PageRequest.of(safePage - 1, safeSize));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("page", safePage);
        response.put("size", safeSize);
        response.put("total", result.getTotalElements());
        response.put("data", result.getContent().stream().map(this::toMap).toList());
        return response;
    }

    private Map<String, Object> toMap(AuditLog log) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", log.getId());
        item.put("operator", log.getOperatorName());
        item.put("role", log.getOperatorRole());
        item.put("action", log.getActionCode());
        item.put("actionName", log.getActionName());
        item.put("targetType", log.getTargetType());
        item.put("targetId", log.getTargetId());
        item.put("summary", log.getSummary());
        item.put("status", log.getStatus());
        item.put("ip", log.getIpAddress());
        item.put("createdAt", log.getCreatedAt());
        return item;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
