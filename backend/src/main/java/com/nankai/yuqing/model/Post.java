package com.nankai.yuqing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子实体 · 对应校园集市等平台原始数据
 */
@Entity
@Table(name = "posts")
public class Post {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "title")
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "author")
    private String author;

    @Column(name = "author_avatar")
    private String authorAvatar;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "publish_timestamp")
    private Long publishTimestamp;

    @Column(name = "category_id")
    private String categoryId;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "comment_count")
    private Integer commentCount = 0;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "is_anonymous")
    private Integer isAnonymous = 0;

    @Column(name = "image_urls", length = 2000)
    private String imageUrlsStr;

    @Transient
    private List<String> imageUrls;

    // 分析结果字段
    @Column(name = "safety_category")
    private String safetyCategory;

    @Column(name = "emotion")
    private String emotion;

    @Column(name = "risk_score")
    private Integer riskScore = 0;

    @Column(name = "risk_level")
    private String riskLevel = "低";

    @Column(name = "location")
    private String location;

    @Column(name = "problem")
    private String problem;

    @Column(name = "demand")
    private String demand;

    @Column(name = "event_id")
    private String eventId;

    // 构造函数
    public Post() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getAuthorAvatar() { return authorAvatar; }
    public void setAuthorAvatar(String authorAvatar) { this.authorAvatar = authorAvatar; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public Long getPublishTimestamp() { return publishTimestamp; }
    public void setPublishTimestamp(Long publishTimestamp) { this.publishTimestamp = publishTimestamp; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Integer isAnonymous) { this.isAnonymous = isAnonymous; }
    public String getImageUrlsStr() { return imageUrlsStr; }
    public void setImageUrlsStr(String imageUrlsStr) { this.imageUrlsStr = imageUrlsStr; }
    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }
    public String getSafetyCategory() { return safetyCategory; }
    public void setSafetyCategory(String safetyCategory) { this.safetyCategory = safetyCategory; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getDemand() { return demand; }
    public void setDemand(String demand) { this.demand = demand; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
}
