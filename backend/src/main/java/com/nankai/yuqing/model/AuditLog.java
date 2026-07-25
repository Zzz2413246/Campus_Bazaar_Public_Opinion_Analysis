package com.nankai.yuqing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 系统关键写操作审计记录，不保存请求正文或敏感凭证。 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_action", columnList = "action_code")
})
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_name", nullable = false, length = 100)
    private String operatorName;

    @Column(name = "operator_role", nullable = false, length = 40)
    private String operatorRole;

    @Column(name = "action_code", nullable = false, length = 60)
    private String actionCode;

    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id", length = 255)
    private String targetId;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperatorRole() { return operatorRole; }
    public void setOperatorRole(String operatorRole) { this.operatorRole = operatorRole; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
