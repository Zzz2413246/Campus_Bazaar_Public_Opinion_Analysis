package com.nankai.yuqing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 安全事件实体 · 聚合多条帖子形成
 */
@Entity
@Table(name = "events")
public class EventEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "title")
    private String title;

    @Column(name = "category")
    private String category;

    @Column(name = "risk")
    private String risk;

    @Column(name = "risk_score")
    private Integer riskScore = 0;

    @Column(name = "status")
    private String status = "待研判";

    @Column(name = "post_count")
    private Integer postCount = 0;

    @Column(name = "affected_range")
    private String affectedRange;

    @Column(name = "urgency")
    private String urgency;

    @Column(name = "emotion_summary")
    private String emotionSummary;

    @Column(name = "summary")
    private String summary;

    @Column(name = "assignee")
    private String assignee;

    @Column(name = "due_at")
    private LocalDateTime dueAt;

    @Column(name = "resolution", length = 2000)
    private String resolution;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EventEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRisk() { return risk; }
    public void setRisk(String risk) { this.risk = risk; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPostCount() { return postCount; }
    public void setPostCount(Integer postCount) { this.postCount = postCount; }
    public String getAffectedRange() { return affectedRange; }
    public void setAffectedRange(String affectedRange) { this.affectedRange = affectedRange; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getEmotionSummary() { return emotionSummary; }
    public void setEmotionSummary(String emotionSummary) { this.emotionSummary = emotionSummary; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
