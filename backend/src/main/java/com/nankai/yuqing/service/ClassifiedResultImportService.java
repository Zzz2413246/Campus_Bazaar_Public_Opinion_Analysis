package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDateTime;

/** 将外部已经完成筛选和深度分析的结果同步为平台权威数据。 */
@Service
public class ClassifiedResultImportService {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final EventRepository eventRepository;
    private final AnalysisService analysisService;

    public ClassifiedResultImportService(PostRepository postRepository,
                                         PostCommentRepository commentRepository,
                                         EventRepository eventRepository,
                                         AnalysisService analysisService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.eventRepository = eventRepository;
        this.analysisService = analysisService;
    }

    @Transactional
    public Map<String, Object> synchronize(List<JsonNode> rawResults) {
        Map<String, JsonNode> retained = new LinkedHashMap<>();
        int nonSafety = 0;
        int normalizedUncertain = 0;
        for (JsonNode item : rawResults) {
            String id = text(item, "post_id");
            String label = text(item, "overall_screening_label").toUpperCase(Locale.ROOT);
            if (id.isBlank()) continue;
            // 外部任务少量失败或未返回标签时仍保留记录，进入人工复核队列，避免
            // 因静默丢数造成看板、报告与原始数据的口径不一致。
            if (!"SAFETY".equals(label) && !"UNCERTAIN".equals(label) && !"NON_SAFETY".equals(label)) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("overall_screening_label", "UNCERTAIN");
                ((com.fasterxml.jackson.databind.node.ObjectNode) item).put("processing_status", "NEEDS_VERIFICATION");
                label = "UNCERTAIN";
                normalizedUncertain++;
            }
            if ("NON_SAFETY".equals(label)) {
                nonSafety++;
            }
            retained.put(id, item);
        }

        Set<String> retainedIds = retained.keySet();
        Set<String> retainedCommentIds = new HashSet<>();
        for (JsonNode item : retained.values()) {
            JsonNode analyses = item.path("full_analysis").path("comment_analyses");
            if (!analyses.isArray()) continue;
            for (JsonNode node : analyses) {
                String commentId = text(node, "comment_id");
                if (!commentId.isBlank()) retainedCommentIds.add(commentId);
            }
        }
        Map<String, Post> existingPosts = new HashMap<>();
        for (Post post : postRepository.findAll()) existingPosts.put(post.getId(), post);

        List<PostComment> obsoleteComments = commentRepository.findAll().stream()
            .filter(comment -> !retainedIds.contains(comment.getThreadId())
                || !retainedCommentIds.contains(comment.getId()))
            .toList();
        for (int from = 0; from < obsoleteComments.size(); from += 250) {
            commentRepository.deleteAllInBatch(
                obsoleteComments.subList(from, Math.min(from + 250, obsoleteComments.size())));
        }

        List<Post> obsoletePosts = existingPosts.values().stream()
            .filter(post -> !retainedIds.contains(post.getId()))
            .toList();
        for (int from = 0; from < obsoletePosts.size(); from += 250) {
            postRepository.deleteAllInBatch(
                obsoletePosts.subList(from, Math.min(from + 250, obsoletePosts.size())));
        }

        Map<String, PostComment> existingComments = new HashMap<>();
        for (PostComment comment : commentRepository.findAll()) existingComments.put(comment.getId(), comment);

        List<Post> posts = new ArrayList<>();
        List<PostComment> comments = new ArrayList<>();
        int created = 0;
        int uncertain = 0;
        int confirmedSafety = 0;
        for (Map.Entry<String, JsonNode> entry : retained.entrySet()) {
            String id = entry.getKey();
            JsonNode item = entry.getValue();
            Post post = existingPosts.get(id);
            if (post == null) {
                post = new Post();
                post.setId(id);
                post.setAuthor("匿名用户");
                post.setIsAnonymous(1);
                post.setCategoryName("校园集市（已分类）");
                created++;
            }
            ReviewSnapshot reviewSnapshot = ReviewSnapshot.capture(post);
            applyResult(post, item);
            reviewSnapshot.restore(post);
            posts.add(post);
            comments.addAll(applyComments(post, item, existingComments));
            if ("UNCERTAIN".equals(post.getScreeningLabel())) uncertain++;
            if ("SAFETY".equals(post.getScreeningLabel())) confirmedSafety++;
        }

        postRepository.saveAll(posts);
        if (!comments.isEmpty()) commentRepository.saveAll(comments);
        analysisService.aggregateEvents();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceRecords", rawResults.size());
        result.put("retained", posts.size());
        result.put("confirmedSafety", confirmedSafety);
        result.put("uncertain", uncertain);
        result.put("nonSafety", nonSafety);
        result.put("normalizedUncertain", normalizedUncertain);
        result.put("createdPlaceholders", created);
        result.put("removedOldPosts", obsoletePosts.size());
        result.put("comments", comments.size());
        return result;
    }

    private void applyResult(Post post, JsonNode item) {
        String label = text(item, "overall_screening_label").toUpperCase(Locale.ROOT);
        JsonNode full = item.path("full_analysis");
        JsonNode postAnalysis = full.path("post_analysis");
        JsonNode discussion = full.path("discussion_analysis");
        String externalCategory = text(postAnalysis, "safety_category");
        String category = "NON_SAFETY".equals(label)
            ? null : SafetyCategoryStandard.fromExternal(externalCategory, label);
        String evidence = joinedText(postAnalysis.path("evidence_spans"));
        String reason = text(postAnalysis, "reason");

        if (blank(post.getTitle())) post.setTitle(titleFrom(evidence, reason, label));
        if (blank(post.getContent())) {
            post.setContent(!evidence.isBlank() ? evidence
                : "该条记录未附带原始正文，请结合帖子编号进行人工核实。");
        }
        if (blank(post.getCategoryName())) post.setCategoryName("校园集市（已分类）");
        post.setSafetyCategory(category);
        post.setScreeningLabel(label);
        post.setProcessingStatus(text(item, "processing_status"));
        post.setAnalysisReason(!reason.isBlank() ? reason
            : "NON_SAFETY".equals(label)
                ? "外部分类结果判定为非安全内容，未进入深度分析。"
                : "分类任务未返回完整分析，需人工核实原始内容。");
        post.setEvidenceSpans(evidence);
        post.setDiscussionSummary(text(discussion, "discussion_summary"));
        post.setControversies(text(discussion, "controversies"));
        post.setSafetyClues(text(discussion, "safety_clues"));
        post.setProblem("外部最终分类：" + (category == null ? "非安全内容" : category));
        post.setTopic(category);
        post.setAnalysisBasis("not_safety".equals(externalCategory) && "SAFETY".equals(label)
            ? "评论区安全线索（外部最终分类）" : "外部最终分类");
        post.setAnalysisVersion(AnalysisService.ANALYSIS_VERSION);
        post.setClassificationConfidence(confidence(postAnalysis, item.path("post_screening")));
        post.setEmotion(dominantEmotion(discussion.path("sentiment_distribution")));
        post.setAnalyzedCommentCount(integer(discussion, "analyzed_comments"));
        post.setNegativeCommentCount(sentimentCount(discussion.path("sentiment_distribution"), "NEGATIVE"));
        post.setCommentSafetyCount(safetyCommentCount(full.path("comment_analyses")));
        post.setCommentRiskAdjustment(0);
        post.setCommentSuggestedCategory(null);
        post.setCommentSuggestionCount(0);
        post.setCommentSignal(compact(text(discussion, "safety_clues"), 1000));
        post.setLikeCount(integer(discussion, "like_count", post.getLikeCount()));
        post.setCommentCount(integer(discussion, "reported_comment_count", post.getCommentCount()));
        post.setRiskScore("SAFETY".equals(label) ? 50 : "UNCERTAIN".equals(label) ? 25 : 0);
        post.setRiskLevel("SAFETY".equals(label) ? "中" : "低");
        post.setProvidedRiskLevel(null);
        post.setEventId(null);
        post.setReviewStatus("UNCERTAIN".equals(label) ? "待复核" : "已确认");
        post.setReviewedCategory(null);
        post.setReviewedRiskLevel(null);
        post.setReviewedEmotion(null);
        post.setReviewNote(null);
        post.setReviewer(null);
        post.setReviewedAt(null);
    }

    private List<PostComment> applyComments(Post post,
                                            JsonNode item,
                                            Map<String, PostComment> existing) {
        List<PostComment> changed = new ArrayList<>();
        JsonNode analyses = item.path("full_analysis").path("comment_analyses");
        if (!analyses.isArray()) return changed;
        for (JsonNode node : analyses) {
            String id = text(node, "comment_id");
            if (id.isBlank()) continue;
            PostComment comment = existing.computeIfAbsent(id, ignored -> new PostComment());
            comment.setId(id);
            comment.setThreadId(post.getId());
            comment.setParentCommentId(emptyToNull(text(node, "parent_comment_id")));
            comment.setRootCommentId(emptyToNull(text(node, "root_comment_id")));
            comment.setIsReply(comment.getParentCommentId() == null ? 0 : 1);
            comment.setReplyDepth(comment.getParentCommentId() == null ? 0 : 1);
            comment.setContent(text(node, "content"));
            comment.setEmotion(emotionName(text(node, "sentiment")));
            comment.setSafetyCategory(node.path("is_safety_related").asBoolean(false)
                ? post.getSafetyCategory() : null);
            comment.setEvidenceScore(node.path("is_safety_related").asBoolean(false) ? 100 : 0);
            comment.setAnalysisVersion(AnalysisService.ANALYSIS_VERSION);
            changed.add(comment);
        }
        return changed;
    }

    private int safetyCommentCount(JsonNode analyses) {
        if (!analyses.isArray()) return 0;
        int count = 0;
        for (JsonNode node : analyses) if (node.path("is_safety_related").asBoolean(false)) count++;
        return count;
    }

    private int confidence(JsonNode postAnalysis, JsonNode screening) {
        double value = postAnalysis.path("confidence").asDouble(
            screening.path("confidence").asDouble(0));
        return (int) Math.round(Math.max(0, Math.min(1, value)) * 100);
    }

    private String dominantEmotion(JsonNode distribution) {
        int positive = sentimentCount(distribution, "POSITIVE");
        int neutral = sentimentCount(distribution, "NEUTRAL");
        int negative = sentimentCount(distribution, "NEGATIVE");
        if (negative > neutral && negative >= positive) return "负面";
        if (positive > neutral && positive > negative) return "正面";
        return "中性";
    }

    private int sentimentCount(JsonNode distribution, String key) {
        return distribution.isObject() ? distribution.path(key).asInt(0) : 0;
    }

    private String emotionName(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "POSITIVE" -> "正面";
            case "NEGATIVE" -> "负面";
            default -> "中性";
        };
    }

    private String titleFrom(String evidence, String reason, String label) {
        String source = !evidence.isBlank() ? evidence.lines().findFirst().orElse("") : reason;
        if (source.isBlank()) {
            source = "UNCERTAIN".equals(label) ? "待核实的校园安全线索"
                : "NON_SAFETY".equals(label) ? "已完成分类的非安全帖子" : "校园安全线索";
        }
        return source.length() > 60 ? source.substring(0, 60) + "…" : source;
    }

    private String joinedText(JsonNode node) {
        if (node.isArray()) {
            List<String> values = new ArrayList<>();
            node.forEach(value -> {
                String text = value.asText("").trim();
                if (!text.isBlank()) values.add(text);
            });
            return String.join("\n", values);
        }
        return node.asText("").trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").trim();
    }

    private Integer integer(JsonNode node, String field) {
        return integer(node, field, 0);
    }

    private Integer integer(JsonNode node, String field, Integer fallback) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : Objects.requireNonNullElse(fallback, 0);
    }

    private String compact(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private String emptyToNull(String value) { return value.isBlank() ? null : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }

    /** 启动同步只刷新外部分析字段，不覆盖管理人员已经提交的复核结论。 */
    private record ReviewSnapshot(
            boolean preserved,
            String status,
            String category,
            String riskLevel,
            String emotion,
            String note,
            String reviewer,
            LocalDateTime reviewedAt) {

        static ReviewSnapshot capture(Post post) {
            boolean preserved = post.getReviewedAt() != null;
            return new ReviewSnapshot(preserved, post.getReviewStatus(), post.getReviewedCategory(),
                post.getReviewedRiskLevel(), post.getReviewedEmotion(), post.getReviewNote(),
                post.getReviewer(), post.getReviewedAt());
        }

        void restore(Post post) {
            if (!preserved) return;
            post.setReviewStatus(status);
            post.setReviewedCategory(category);
            post.setReviewedRiskLevel(riskLevel);
            post.setReviewedEmotion(emotion);
            post.setReviewNote(note);
            post.setReviewer(reviewer);
            post.setReviewedAt(reviewedAt);
        }
    }
}
