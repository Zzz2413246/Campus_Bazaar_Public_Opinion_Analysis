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

    /** 外部AI直接提供的低/中/高标签；存在时优先于本地规则推断结果。 */
    @Column(name = "provided_risk_level")
    private String providedRiskLevel;

    @Column(name = "location")
    private String location;

    @Column(name = "problem")
    private String problem;

    @Column(name = "demand")
    private String demand;

    @Column(name = "event_id")
    private String eventId;

    /** 当前分析规则版本，便于后续规则升级后识别需要重算的数据。 */
    @Column(name = "analysis_version")
    private String analysisVersion;

    /** 安全分类置信度（0-100），非安全内容为 0。 */
    @Column(name = "classification_confidence")
    private Integer classificationConfidence = 0;

    /** 比安全大类更细的话题标签，用于事件聚合。 */
    @Column(name = "topic")
    private String topic;

    /** 实际关联并参与分析的评论数，与抓取时帖子自带的 commentCount 分开保存。 */
    @Column(name = "analyzed_comment_count")
    private Integer analyzedCommentCount = 0;

    @Column(name = "negative_comment_count")
    private Integer negativeCommentCount = 0;

    /** 与最终安全分类一致的评论佐证数量。 */
    @Column(name = "comment_safety_count")
    private Integer commentSafetyCount = 0;

    /** 评论信号对单帖风险分的增量，限制在 0-12 分。 */
    @Column(name = "comment_risk_adjustment")
    private Integer commentRiskAdjustment = 0;

    @Column(name = "comment_signal", length = 1000)
    private String commentSignal;

    /** 评论共识提示的待复核类别；不直接覆盖原帖分类，也不参与自动评分。 */
    @Column(name = "comment_suggested_category")
    private String commentSuggestedCategory;

    @Column(name = "comment_suggestion_count")
    private Integer commentSuggestionCount = 0;

    /** 原帖文本 / 原帖文本+评论佐证 / 评论共识补充。 */
    @Column(name = "analysis_basis")
    private String analysisBasis;

    /** 外部最终分类的综合标签：SAFETY / UNCERTAIN。 */
    @Column(name = "screening_label")
    private String screeningLabel;

    @Column(name = "processing_status")
    private String processingStatus;

    @Column(name = "analysis_reason", columnDefinition = "TEXT")
    private String analysisReason;

    @Column(name = "evidence_spans", columnDefinition = "TEXT")
    private String evidenceSpans;

    @Column(name = "discussion_summary", columnDefinition = "TEXT")
    private String discussionSummary;

    @Column(name = "controversies", columnDefinition = "TEXT")
    private String controversies;

    @Column(name = "safety_clues", columnDefinition = "TEXT")
    private String safetyClues;

    /** 人工复核状态：待复核 / 已确认 / 已修正 / 无关内容。 */
    @Column(name = "review_status")
    private String reviewStatus = "待复核";

    /** 人工最终分类；为空时使用原始 AI 分类。 */
    @Column(name = "reviewed_category")
    private String reviewedCategory;

    /** 人工最终风险等级；为空时使用原始 AI 风险等级。 */
    @Column(name = "reviewed_risk_level")
    private String reviewedRiskLevel;

    /** 人工最终情绪；为空时使用原始 AI 情绪。 */
    @Column(name = "reviewed_emotion")
    private String reviewedEmotion;

    @Column(name = "review_note", length = 2000)
    private String reviewNote;

    @Column(name = "reviewer")
    private String reviewer;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

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
    public String getProvidedRiskLevel() { return providedRiskLevel; }
    public void setProvidedRiskLevel(String providedRiskLevel) { this.providedRiskLevel = providedRiskLevel; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getProblem() { return problem; }
    public void setProblem(String problem) { this.problem = problem; }
    public String getDemand() { return demand; }
    public void setDemand(String demand) { this.demand = demand; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAnalysisVersion() { return analysisVersion; }
    public void setAnalysisVersion(String analysisVersion) { this.analysisVersion = analysisVersion; }
    public Integer getClassificationConfidence() { return classificationConfidence; }
    public void setClassificationConfidence(Integer classificationConfidence) { this.classificationConfidence = classificationConfidence; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public Integer getAnalyzedCommentCount() { return analyzedCommentCount; }
    public void setAnalyzedCommentCount(Integer analyzedCommentCount) { this.analyzedCommentCount = analyzedCommentCount; }
    public Integer getNegativeCommentCount() { return negativeCommentCount; }
    public void setNegativeCommentCount(Integer negativeCommentCount) { this.negativeCommentCount = negativeCommentCount; }
    public Integer getCommentSafetyCount() { return commentSafetyCount; }
    public void setCommentSafetyCount(Integer commentSafetyCount) { this.commentSafetyCount = commentSafetyCount; }
    public Integer getCommentRiskAdjustment() { return commentRiskAdjustment; }
    public void setCommentRiskAdjustment(Integer commentRiskAdjustment) { this.commentRiskAdjustment = commentRiskAdjustment; }
    public String getCommentSignal() { return commentSignal; }
    public void setCommentSignal(String commentSignal) { this.commentSignal = commentSignal; }
    public String getCommentSuggestedCategory() { return commentSuggestedCategory; }
    public void setCommentSuggestedCategory(String commentSuggestedCategory) { this.commentSuggestedCategory = commentSuggestedCategory; }
    public Integer getCommentSuggestionCount() { return commentSuggestionCount; }
    public void setCommentSuggestionCount(Integer commentSuggestionCount) { this.commentSuggestionCount = commentSuggestionCount; }
    public String getAnalysisBasis() { return analysisBasis; }
    public void setAnalysisBasis(String analysisBasis) { this.analysisBasis = analysisBasis; }
    public String getScreeningLabel() { return screeningLabel; }
    public void setScreeningLabel(String screeningLabel) { this.screeningLabel = screeningLabel; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public String getAnalysisReason() { return analysisReason; }
    public void setAnalysisReason(String analysisReason) { this.analysisReason = analysisReason; }
    public String getEvidenceSpans() { return evidenceSpans; }
    public void setEvidenceSpans(String evidenceSpans) { this.evidenceSpans = evidenceSpans; }
    public String getDiscussionSummary() { return discussionSummary; }
    public void setDiscussionSummary(String discussionSummary) { this.discussionSummary = discussionSummary; }
    public String getControversies() { return controversies; }
    public void setControversies(String controversies) { this.controversies = controversies; }
    public String getSafetyClues() { return safetyClues; }
    public void setSafetyClues(String safetyClues) { this.safetyClues = safetyClues; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewedCategory() { return reviewedCategory; }
    public void setReviewedCategory(String reviewedCategory) { this.reviewedCategory = reviewedCategory; }
    public String getReviewedRiskLevel() { return reviewedRiskLevel; }
    public void setReviewedRiskLevel(String reviewedRiskLevel) { this.reviewedRiskLevel = reviewedRiskLevel; }
    public String getReviewedEmotion() { return reviewedEmotion; }
    public void setReviewedEmotion(String reviewedEmotion) { this.reviewedEmotion = reviewedEmotion; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getReviewer() { return reviewer; }
    public void setReviewer(String reviewer) { this.reviewer = reviewer; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}
