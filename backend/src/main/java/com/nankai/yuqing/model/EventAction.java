package com.nankai.yuqing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 事件处置记录：用于保存每次状态流转和人工处置意见。
 */
@Entity
@Table(name = "event_actions")
public class EventAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "operator_name")
    private String operatorName;

    @Column(name = "action_name", nullable = false)
    private String actionName;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public EventAction() {}

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
