package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 评论数据增量同步服务。
 *
 * <p>评论 ID 用于去重；已有评论的正文、互动量等字段发生变化时会更新。
 * 原始评论中的用户标识、昵称和头像不会写入数据库。</p>
 */
@Service
public class CommentImportService {

    private static final Logger log = LoggerFactory.getLogger(CommentImportService.class);
    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentImportService(PostCommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    @Transactional
    public Map<String, Object> importComments(List<Map<String, Object>> rawData) {
        int imported = 0;
        int updated = 0;
        int skipped = 0;
        int errors = 0;
        int duplicatesMerged = 0;

        Map<String, PostComment> existing = new HashMap<>();
        for (PostComment comment : commentRepository.findAll()) {
            existing.put(comment.getId(), comment);
        }
        Set<String> postIds = new HashSet<>(postRepository.findAllIds());
        Set<String> seenIds = new HashSet<>();
        Set<String> newIds = new HashSet<>();
        Set<String> updatedIds = new HashSet<>();
        Map<String, String> finalThreadIds = new HashMap<>();
        Map<String, PostComment> changed = new LinkedHashMap<>();

        log.info("开始导入 {} 条评论数据", rawData.size());
        for (Map<String, Object> item : rawData) {
            String id = text(item.get("comment_id"));
            String threadId = text(item.get("thread_id"));
            if (id.isBlank() || threadId.isBlank()) {
                errors++;
                continue;
            }
            if (!seenIds.add(id)) duplicatesMerged++;
            finalThreadIds.put(id, threadId);

            PostComment incoming = convert(item);
            if (incoming == null) {
                errors++;
                continue;
            }

            PostComment current = existing.get(id);
            if (current == null) {
                existing.put(id, incoming);
                newIds.add(id);
                changed.put(id, incoming);
                imported++;
            } else if (mergeRawFields(current, incoming)) {
                changed.put(id, current);
                if (!newIds.contains(id) && updatedIds.add(id)) updated++;
            } else {
                skipped++;
            }
        }

        if (!changed.isEmpty()) commentRepository.saveAll(new ArrayList<>(changed.values()));
        long unmatched = finalThreadIds.values().stream()
            .filter(threadId -> !postIds.contains(threadId))
            .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rawData.size());
        result.put("imported", imported);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("unmatched", unmatched);
        result.put("duplicatesMerged", duplicatesMerged);
        log.info("评论导入完成：{}", result);
        return result;
    }

    public Map<String, Object> getStats() {
        List<PostComment> comments = commentRepository.findAll();
        Set<String> postIds = new HashSet<>(postRepository.findAllIds());
        Set<String> threads = new HashSet<>();
        Set<String> matchedThreads = new HashSet<>();
        long matchedComments = 0;
        long negativeComments = 0;
        long safetyComments = 0;

        for (PostComment comment : comments) {
            threads.add(comment.getThreadId());
            if (postIds.contains(comment.getThreadId())) {
                matchedComments++;
                matchedThreads.add(comment.getThreadId());
            }
            if ("负面".equals(comment.getEmotion())) negativeComments++;
            if (comment.getSafetyCategory() != null) safetyComments++;
        }

        List<Post> posts = postRepository.findAll();
        long assistedPosts = posts.stream()
            .filter(p -> safe(p.getAnalyzedCommentCount()) > 0)
            .count();
        long adjustedPosts = posts.stream()
            .filter(p -> safe(p.getCommentRiskAdjustment()) > 0)
            .count();
        long suggestedPosts = posts.stream()
            .filter(p -> p.getCommentSuggestedCategory() != null)
            .count();
        double coverage = postIds.isEmpty() ? 0 : assistedPosts * 100.0 / postIds.size();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalComments", comments.size());
        stats.put("commentThreads", threads.size());
        stats.put("matchedComments", matchedComments);
        stats.put("matchedThreads", matchedThreads.size());
        stats.put("unmatchedComments", Math.max(0, comments.size() - matchedComments));
        stats.put("assistedPosts", assistedPosts);
        stats.put("adjustedPosts", adjustedPosts);
        stats.put("suggestedPosts", suggestedPosts);
        stats.put("coverage", Math.round(coverage * 100.0) / 100.0);
        stats.put("negativeComments", negativeComments);
        stats.put("safetyComments", safetyComments);
        return stats;
    }

    /**
     * 检查帖子当前保存的评论分析数量是否与评论表一致。
     * 用于处理“先有未匹配评论，后增量加入对应帖子”的启动场景。
     */
    public boolean hasUnappliedLinks() {
        Map<String, Integer> counts = new HashMap<>();
        for (PostComment comment : commentRepository.findAll()) {
            counts.merge(comment.getThreadId(), 1, Integer::sum);
        }
        for (Post post : postRepository.findAll()) {
            int expected = counts.getOrDefault(post.getId(), 0);
            if (safe(post.getAnalyzedCommentCount()) != expected) return true;
        }
        return false;
    }

    @Transactional
    public void clearAll() {
        commentRepository.deleteAll();
    }

    private PostComment convert(Map<String, Object> item) {
        try {
            PostComment comment = new PostComment();
            comment.setId(text(item.get("comment_id")));
            comment.setThreadId(text(item.get("thread_id")));
            comment.setParentCommentId(text(item.get("parent_comment_id")));
            comment.setRootCommentId(text(item.get("root_comment_id")));
            comment.setIsReply(toInt(item.get("is_reply")));
            comment.setReplyDepth(toInt(item.get("reply_depth")));
            comment.setContent(text(item.get("content")));
            comment.setPublishTime(parseTime(item.get("publish_time")));
            comment.setPublishTimestamp(toLong(item.get("publish_timestamp")));
            comment.setLikeCount(toInt(item.get("like_count")));
            comment.setDislikeCount(toInt(item.get("dislike_count")));
            comment.setIsAuthor(toInt(item.get("is_author")));
            return comment;
        } catch (Exception e) {
            log.warn("转换评论失败：{}", item.get("comment_id"), e);
            return null;
        }
    }

    private boolean mergeRawFields(PostComment current, PostComment incoming) {
        boolean changed =
            !Objects.equals(current.getThreadId(), incoming.getThreadId()) ||
            !Objects.equals(current.getParentCommentId(), incoming.getParentCommentId()) ||
            !Objects.equals(current.getRootCommentId(), incoming.getRootCommentId()) ||
            !Objects.equals(current.getIsReply(), incoming.getIsReply()) ||
            !Objects.equals(current.getReplyDepth(), incoming.getReplyDepth()) ||
            !Objects.equals(current.getContent(), incoming.getContent()) ||
            !Objects.equals(current.getPublishTime(), incoming.getPublishTime()) ||
            !Objects.equals(current.getPublishTimestamp(), incoming.getPublishTimestamp()) ||
            !Objects.equals(current.getLikeCount(), incoming.getLikeCount()) ||
            !Objects.equals(current.getDislikeCount(), incoming.getDislikeCount()) ||
            !Objects.equals(current.getIsAuthor(), incoming.getIsAuthor());
        if (!changed) return false;

        current.setThreadId(incoming.getThreadId());
        current.setParentCommentId(incoming.getParentCommentId());
        current.setRootCommentId(incoming.getRootCommentId());
        current.setIsReply(incoming.getIsReply());
        current.setReplyDepth(incoming.getReplyDepth());
        current.setContent(incoming.getContent());
        current.setPublishTime(incoming.getPublishTime());
        current.setPublishTimestamp(incoming.getPublishTimestamp());
        current.setLikeCount(incoming.getLikeCount());
        current.setDislikeCount(incoming.getDislikeCount());
        current.setIsAuthor(incoming.getIsAuthor());
        current.setAnalysisVersion(null);
        current.setEmotion(null);
        current.setSafetyCategory(null);
        current.setEvidenceScore(0);
        return true;
    }

    private LocalDateTime parseTime(Object value) {
        String text = text(value);
        if (text.isBlank()) return null;
        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
