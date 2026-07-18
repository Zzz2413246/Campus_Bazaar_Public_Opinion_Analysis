package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评论分析结果查询接口。返回内容不包含评论者个人标识。
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentController(PostCommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    @GetMapping("/post/{postId}")
    public Map<String, Object> listForPost(
            @PathVariable String postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<PostComment> result = commentRepository.findByThreadIdOrderByPublishTimeAsc(
            postId, PageRequest.of(safePage - 1, safeSize));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("postId", postId);
        response.put("page", safePage);
        response.put("size", safeSize);
        response.put("total", result.getTotalElements());
        response.put("data", result.getContent().stream().map(this::toMap).toList());

        Post post = postRepository.findById(postId).orElse(null);
        if (post != null) {
            response.put("analysisBasis", post.getAnalysisBasis());
            response.put("commentSignal", post.getCommentSignal());
            response.put("commentRiskAdjustment", post.getCommentRiskAdjustment());
            response.put("commentSuggestedCategory", post.getCommentSuggestedCategory());
        }
        return response;
    }

    private Map<String, Object> toMap(PostComment comment) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", comment.getId());
        result.put("content", comment.getContent());
        result.put("publishTime", comment.getPublishTime() == null
            ? "" : comment.getPublishTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
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
}
