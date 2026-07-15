package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
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

    public PostController(PostRepository postRepository) {
        this.postRepository = postRepository;
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
        List<Post> posts = postRepository.searchPosts(keyword, category, emotion, source);

        // 分页
        int total = posts.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);
        List<Post> pageData = posts.subList(from, to);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("data", pageData.stream().map(this::toMap).toList());
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Post post = postRepository.findById(id).orElse(null);
        if (post == null) {
            return Map.of("error", "帖子不存在");
        }
        return toMap(post);
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
