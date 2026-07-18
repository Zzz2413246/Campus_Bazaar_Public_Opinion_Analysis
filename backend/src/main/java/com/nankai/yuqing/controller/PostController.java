package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 帖子监测接口
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;

    public PostController(PostRepository postRepository, PostCommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String emotion,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 200));
        Page<Post> resultPage = postRepository.searchPosts(
            blankToNull(keyword), blankToNull(category), blankToNull(emotion), blankToNull(source),
            PageRequest.of(safePage - 1, safeSize));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", resultPage.getTotalElements());
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("data", resultPage.getContent().stream().map(this::toMap).toList());
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

        Map<String, Object> result = toMap(post);
        Map<String, Object> commentResult = new LinkedHashMap<>();
        commentResult.put("page", safePage);
        commentResult.put("size", safeSize);
        commentResult.put("total", comments.getTotalElements());
        commentResult.put("data", comments.getContent().stream().map(this::toCommentMap).toList());
        result.put("comments", commentResult);
        return result;
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

    private Map<String, Object> toMap(Post p) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("title", p.getTitle());
        m.put("content", p.getContent());
        m.put("author", p.getAuthor());
        m.put("authorAvatar", p.getAuthorAvatar());
        m.put("publishTime", p.getPublishTime() != null ? p.getPublishTime().format(fmt) : "");
        m.put("categoryName", p.getCategoryName());
        m.put("commentCount", p.getCommentCount());
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
        m.put("safetyCategory", p.getSafetyCategory() != null ? p.getSafetyCategory() : "其他");
        m.put("emotion", p.getEmotion() != null ? p.getEmotion() : "中性");
        m.put("riskScore", p.getRiskScore());
        m.put("riskLevel", p.getRiskLevel());
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
        m.put("source", p.getCategoryName());

        // 时间描述
        m.put("timeDesc", timeDesc(p.getPublishTime()));
        return m;
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
