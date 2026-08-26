package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.model.SafetyRelevance;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import com.nankai.yuqing.service.SafetyCategoryStandard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 帖子监测接口
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final AnalysisService analysisService;

    public PostController(PostRepository postRepository,
                          PostCommentRepository commentRepository,
                          AnalysisService analysisService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.analysisService = analysisService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String emotion,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        String safeSortBy = Set.of("latest", "risk", "heat").contains(sortBy) ? sortBy : "latest";
        Page<Post> resultPage = postRepository.searchPosts(
            blankToNull(keyword), blankToNull(category), blankToNull(emotion), blankToNull(source),
            blankToNull(reviewStatus), safeSortBy,
            PageRequest.of(safePage - 1, safeSize));
        List<Post> posts = resultPage.getContent();
        Map<String, Long> actualCommentCounts = actualCommentCounts(posts);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", resultPage.getTotalElements());
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("data", posts.stream()
            .map(post -> toMap(post, actualCommentCounts.getOrDefault(post.getId(), 0L)))
            .toList());
        return result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int commentPage,
            @RequestParam(defaultValue = "20") int commentSize) {
        Post post = postRepository.findById(id).orElse(null);
        if (post == null) {
            return Map.of("error", "帖子不存在");
        }
        int safePage = Math.max(1, commentPage);
        int safeSize = Math.max(1, Math.min(commentSize, 100));
        Page<PostComment> comments = commentRepository.findByThreadIdOrderByPublishTimeAsc(
            id, PageRequest.of(safePage - 1, safeSize));

        Map<String, Object> result = toMap(post, comments.getTotalElements());
        Map<String, Object> commentResult = new LinkedHashMap<>();
        commentResult.put("page", safePage);
        commentResult.put("size", safeSize);
        commentResult.put("total", comments.getTotalElements());
        commentResult.put("data", comments.getContent().stream().map(this::toCommentMap).toList());
        result.put("comments", commentResult);
        return result;
    }

    /**
     * 批量确认 AI 结论或批量标记无关内容。一次聚合事件，避免逐条保存时重复计算。
     */
    @PutMapping("/review/batch")
    public Map<String, Object> batchReview(@RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        Object rawIds = body.get("ids");
        if (!(rawIds instanceof Collection<?> idValues)) {
            return Map.of("error", "请选择需要复核的帖子");
        }
        List<String> ids = idValues.stream()
            .map(String::valueOf)
            .map(String::trim)
            .filter(id -> !id.isEmpty())
            .distinct()
            .limit(200)
            .toList();
        if (ids.isEmpty()) return Map.of("error", "请选择需要复核的帖子");

        String action = Objects.toString(body.get("action"), "").trim();
        if (!Set.of("confirm", "irrelevant").contains(action)) {
            return Map.of("error", "批量操作仅支持确认AI或标记无关内容");
        }
        String note = Objects.toString(body.get("note"), "").trim();
        if (note.length() > 2000) return Map.of("error", "复核说明不能超过 2000 个字符");
        if ("irrelevant".equals(action) && note.isEmpty()) {
            return Map.of("error", "批量标记无关内容时必须填写原因");
        }
        String reviewer = authenticatedReviewer(request);
        LocalDateTime reviewedAt = LocalDateTime.now();
        List<Post> posts = postRepository.findAllById(ids);
        for (Post post : posts) {
            if ("confirm".equals(action)) {
                post.setReviewStatus("已确认");
                post.setReviewedCategory(SafetyRelevance.isUnrelated(post.getSafetyRelevance())
                    ? null : defaultString(post.getSafetyCategory(), "其他校园安全"));
                post.setReviewedRiskLevel(aiRiskLevel(post));
                post.setReviewedEmotion(defaultString(post.getEmotion(), "中性"));
            } else {
                post.setReviewStatus("无关内容");
                post.setReviewedCategory(null);
                post.setReviewedRiskLevel("低");
                post.setReviewedEmotion(defaultString(post.getEmotion(), "中性"));
            }
            post.setReviewNote(note);
            post.setReviewer(reviewer.isEmpty() ? "管理员" : reviewer);
            post.setReviewedAt(reviewedAt);
        }
        postRepository.saveAll(posts);
        analysisService.aggregateEvents();
        return Map.of(
            "success", true,
            "updated", posts.size(),
            "missing", Math.max(0, ids.size() - posts.size()));
    }

    /**
     * 保存人工复核结论，同时保留原始 AI 分析结果用于差异对比。
     */
    @PutMapping("/{id}/review")
    public Map<String, Object> review(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        Post post = postRepository.findById(id).orElse(null);
        if (post == null) return Map.of("error", "帖子不存在");

        String action = Objects.toString(body.get("action"), "").trim();
        String note = Objects.toString(body.get("note"), "").trim();
        if (note.length() > 2000) return Map.of("error", "复核说明不能超过 2000 个字符");
        String reviewer = authenticatedReviewer(request);

        switch (action) {
            case "confirm" -> {
                post.setReviewStatus("已确认");
                post.setReviewedCategory(SafetyRelevance.isUnrelated(post.getSafetyRelevance())
                    ? null : defaultString(post.getSafetyCategory(), "其他校园安全"));
                post.setReviewedRiskLevel(aiRiskLevel(post));
                post.setReviewedEmotion(defaultString(post.getEmotion(), "中性"));
            }
            case "correct" -> {
                String category = Objects.toString(body.get("category"), "").trim();
                String riskLevel = Objects.toString(body.get("riskLevel"), "").trim();
                String emotion = Objects.toString(body.get("emotion"), "").trim();
                if (category.isEmpty() || riskLevel.isEmpty() || emotion.isEmpty() || note.isEmpty()) {
                    return Map.of("error", "修正分析时必须填写分类、风险、情绪和复核说明");
                }
                if (!Set.of("高", "中", "低").contains(riskLevel)) {
                    return Map.of("error", "无效的风险等级");
                }
                if (!Set.of("正面", "中性", "负面").contains(emotion)) {
                    return Map.of("error", "无效的情绪类型");
                }
                if (!SafetyCategoryStandard.CATEGORIES.contains(category) && !"非安全内容".equals(category)) {
                    return Map.of("error", "无效的安全分类");
                }
                boolean nonSafety = "非安全内容".equals(category);
                post.setReviewStatus(nonSafety ? "无关内容" : "已修正");
                post.setReviewedCategory(nonSafety ? null : category);
                post.setReviewedRiskLevel(nonSafety ? "低" : riskLevel);
                post.setReviewedEmotion(emotion);
            }
            case "irrelevant" -> {
                if (note.isEmpty()) return Map.of("error", "标记无关内容时必须填写原因");
                post.setReviewStatus("无关内容");
                post.setReviewedCategory(null);
                post.setReviewedRiskLevel("低");
                post.setReviewedEmotion(defaultString(post.getEmotion(), "中性"));
            }
            case "reset" -> {
                post.setReviewStatus("待复核");
                post.setReviewedCategory(null);
                post.setReviewedRiskLevel(null);
                post.setReviewedEmotion(null);
                post.setReviewNote(null);
                post.setReviewer(null);
                post.setReviewedAt(null);
                postRepository.save(post);
                analysisService.aggregateEvents();
                return toMap(post, commentRepository.countByThreadId(post.getId()));
            }
            default -> {
                return Map.of("error", "无效的复核操作");
            }
        }

        post.setReviewNote(note);
        post.setReviewer(reviewer.isEmpty() ? "管理员" : reviewer);
        post.setReviewedAt(LocalDateTime.now());
        postRepository.save(post);
        analysisService.aggregateEvents();
        return toMap(post, commentRepository.countByThreadId(post.getId()));
    }

    private String authenticatedReviewer(HttpServletRequest request) {
        String reviewer = Objects.toString(request.getAttribute("auditOperator"), "管理员").trim();
        return reviewer.isEmpty() ? "管理员" : reviewer.substring(0, Math.min(reviewer.length(), 100));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Map<String, Object> toCommentMap(PostComment comment) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("content", comment.getContent());
        result.put("publishTime", comment.getPublishTime() == null ? "" : comment.getPublishTime().format(fmt));
        result.put("likeCount", comment.getLikeCount());
        result.put("dislikeCount", comment.getDislikeCount());
        result.put("isReply", comment.getIsReply());
        result.put("replyDepth", comment.getReplyDepth());
        result.put("isAuthor", comment.getIsAuthor());
        result.put("emotion", comment.getEmotion());
        result.put("safetyCategory", comment.getSafetyCategory());
        result.put("evidenceScore", comment.getEvidenceScore());
        return result;
    }

    private Map<String, Long> actualCommentCounts(List<Post> posts) {
        if (posts.isEmpty()) return Map.of();
        List<String> ids = posts.stream().map(Post::getId).toList();
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : commentRepository.countByThreadIds(ids)) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Map<String, Object> toMap(Post p, long actualCommentCount) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("content", p.getContent());
        m.put("author", p.getAuthor());
        m.put("authorAvatar", p.getAuthorAvatar());
        m.put("publishTime", p.getPublishTime() != null ? p.getPublishTime().format(fmt) : "");
        m.put("publishTimestamp", p.getPublishTimestamp());
        m.put("categoryName", p.getCategoryName());
        // 页面展示以本地实际关联的评论行为准；抓取时声明值单独保留用于排查数据质量。
        m.put("commentCount", actualCommentCount);
        m.put("sourceCommentCount", p.getCommentCount() == null ? 0 : p.getCommentCount());
        m.put("likeCount", p.getLikeCount());
        m.put("viewCount", p.getViewCount());
        m.put("isAnonymous", p.getIsAnonymous());

        // 图片URL
        List<String> imgs = new ArrayList<>();
        if (p.getImageUrlsStr() != null && !p.getImageUrlsStr().isEmpty()) {
            imgs = Arrays.asList(p.getImageUrlsStr().split(";"));
        }
        m.put("imageUrls", imgs);

        // 分析结果
        String reviewStatus = p.getReviewStatus() == null ? "待复核" : p.getReviewStatus();
        String effectiveCategory = p.getReviewedCategory() != null
            ? p.getReviewedCategory() : displayCategory(p);
        String effectiveEmotion = p.getReviewedEmotion() != null
            ? p.getReviewedEmotion() : defaultString(p.getEmotion(), "中性");
        String effectiveRiskLevel = p.getReviewedRiskLevel() != null
            ? p.getReviewedRiskLevel() : aiRiskLevel(p);

        m.put("safetyCategory", effectiveCategory);
        m.put("emotion", effectiveEmotion);
        m.put("riskLevel", effectiveRiskLevel);
        m.put("aiSafetyCategory", displayCategory(p));
        m.put("aiEmotion", defaultString(p.getEmotion(), "中性"));
        m.put("aiRiskLevel", aiRiskLevel(p));
        m.put("riskLabelSource", p.getProvidedRiskLevel() != null ? "外部AI" : "本地分析");
        m.put("reviewStatus", reviewStatus);
        m.put("reviewedCategory", p.getReviewedCategory());
        m.put("reviewedEmotion", p.getReviewedEmotion());
        m.put("reviewedRiskLevel", p.getReviewedRiskLevel());
        m.put("reviewNote", p.getReviewNote());
        m.put("reviewer", p.getReviewer());
        m.put("reviewedAt", p.getReviewedAt());
        m.put("location", p.getLocation());
        m.put("problem", p.getProblem());
        m.put("demand", p.getDemand());
        m.put("topic", p.getTopic());
        m.put("classificationConfidence", p.getClassificationConfidence());
        m.put("analysisVersion", p.getAnalysisVersion());
        m.put("analyzedCommentCount", p.getAnalyzedCommentCount());
        m.put("negativeCommentCount", p.getNegativeCommentCount());
        int analyzedComments = p.getAnalyzedCommentCount() == null ? 0 : p.getAnalyzedCommentCount();
        int negativeComments = p.getNegativeCommentCount() == null ? 0 : p.getNegativeCommentCount();
        double negativeRatio = analyzedComments == 0 ? 0 : negativeComments * 100.0 / analyzedComments;
        m.put("negativeCommentRatio", Math.round(negativeRatio * 100.0) / 100.0);
        m.put("commentSafetyCount", p.getCommentSafetyCount());
        m.put("commentRiskAdjustment", p.getCommentRiskAdjustment());
        m.put("commentSignal", p.getCommentSignal());
        m.put("commentSuggestedCategory", p.getCommentSuggestedCategory());
        m.put("commentSuggestionCount", p.getCommentSuggestionCount());
        m.put("analysisBasis", p.getAnalysisBasis());
        m.put("safetyRelevance", p.getSafetyRelevance());
        m.put("processingStatus", p.getProcessingStatus());
        m.put("analysisReason", p.getAnalysisReason());
        m.put("evidenceSpans", p.getEvidenceSpans());
        m.put("discussionSummary", p.getDiscussionSummary());
        m.put("controversies", p.getControversies());
        m.put("safetyClues", p.getSafetyClues());
        m.put("source", p.getCategoryName());

        // 时间描述
        m.put("timeDesc", timeDesc(p.getPublishTime()));
        return m;
    }

    private String aiRiskLevel(Post post) {
        return defaultString(
            post.getProvidedRiskLevel() != null
                ? post.getProvidedRiskLevel() : post.getRiskLevel(),
            "低");
    }

    private String displayCategory(Post post) {
        if (SafetyRelevance.isUnrelated(post.getSafetyRelevance())) return "非安全内容";
        return defaultString(post.getSafetyCategory(), "疑似主题无法确定");
    }

    private String timeDesc(java.time.LocalDateTime time) {
        if (time == null) return "";
        long hours = java.time.Duration.between(time, java.time.LocalDateTime.now()).toHours();
        if (hours < 1) return "刚刚";
        if (hours < 24) return hours + "小时前";
        long days = hours / 24;
        if (days < 7) return days + "天前";
        return time.format(DateTimeFormatter.ofPattern("MM月dd日"));
    }
}
