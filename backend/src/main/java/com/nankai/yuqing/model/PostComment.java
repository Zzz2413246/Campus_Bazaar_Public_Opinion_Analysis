package com.nankai.yuqing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 帖子评论实体。
 *
 * <p>只保存分析所需字段，不落库评论者昵称、头像和用户标识，降低原始抓取数据
 * 中个人信息扩散的风险。评论通过 threadId 与 Post.id 关联，无法关联的评论仍可
 * 增量保留，但不会参与帖子风险评分。</p>
 */
@Entity
@Table(name = "post_comments", indexes = {
    @Index(name = "idx_comment_thread_id", columnList = "thread_id"),
    @Index(name = "idx_comment_publish_time", columnList = "publish_time")
})
public class PostComment {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "thread_id", nullable = false)
    private String threadId;

    @Column(name = "parent_comment_id")
    private String parentCommentId;

    @Column(name = "root_comment_id")
    private String rootCommentId;

    @Column(name = "is_reply")
    private Integer isReply = 0;

    @Column(name = "reply_depth")
    private Integer replyDepth = 0;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "publish_timestamp")
    private Long publishTimestamp;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "dislike_count")
    private Integer dislikeCount = 0;

    @Column(name = "is_author")
    private Integer isAuthor = 0;

    /** 评论自身的标准化分析结果，便于解释帖子评分。 */
    @Column(name = "emotion")
    private String emotion;

    @Column(name = "safety_category")
    private String safetyCategory;

    @Column(name = "evidence_score")
    private Integer evidenceScore = 0;

    @Column(name = "analysis_version")
    private String analysisVersion;

    public PostComment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
    public String getRootCommentId() { return rootCommentId; }
    public void setRootCommentId(String rootCommentId) { this.rootCommentId = rootCommentId; }
    public Integer getIsReply() { return isReply; }
    public void setIsReply(Integer isReply) { this.isReply = isReply; }
    public Integer getReplyDepth() { return replyDepth; }
    public void setReplyDepth(Integer replyDepth) { this.replyDepth = replyDepth; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public Long getPublishTimestamp() { return publishTimestamp; }
    public void setPublishTimestamp(Long publishTimestamp) { this.publishTimestamp = publishTimestamp; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getDislikeCount() { return dislikeCount; }
    public void setDislikeCount(Integer dislikeCount) { this.dislikeCount = dislikeCount; }
    public Integer getIsAuthor() { return isAuthor; }
    public void setIsAuthor(Integer isAuthor) { this.isAuthor = isAuthor; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public String getSafetyCategory() { return safetyCategory; }
    public void setSafetyCategory(String safetyCategory) { this.safetyCategory = safetyCategory; }
    public Integer getEvidenceScore() { return evidenceScore; }
    public void setEvidenceScore(Integer evidenceScore) { this.evidenceScore = evidenceScore; }
    public String getAnalysisVersion() { return analysisVersion; }
    public void setAnalysisVersion(String analysisVersion) { this.analysisVersion = analysisVersion; }
}
