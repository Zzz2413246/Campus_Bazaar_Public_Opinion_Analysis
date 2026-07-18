package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 任务二占位实现：标准未到位前不猜测评分逻辑，但数据和调用契约已经固定。 */
@Service
public class Task2AnalysisExtension implements AnalysisTaskExtension {

    private final PostRepository postRepository;

    public Task2AnalysisExtension(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public String code() {
        return "task2";
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code());
        result.put("name", "任务二风险等级接收");
        result.put("state", "READY_FOR_LABELS");
        result.put("ready", true);
        result.put("analysisInput", List.of(
            "id", "title", "content", "publishTime", "categoryName",
            "commentCount", "likeCount", "viewCount", "safetyCategory",
            "emotion", "location", "problem", "demand", "topic"
        ));
        result.put("resultContract", Map.of(
            "field", "riskLevel",
            "allowedValues", List.of("低", "中", "高"),
            "itemExample", Map.of("postId", "帖子ID", "riskLevel", "低")
        ));
        result.put("message", "任务二只需返回每条帖子的低、中、高风险标记，不接收分数或阈值");
        return result;
    }

    @Override
    public Map<String, Object> execute(List<Post> posts, Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>(status());
        result.put("acceptedPosts", posts.size());
        Object rawResults = request == null ? null : request.get("results");
        Collection<?> items = rawResults instanceof Collection<?> collection ? collection : List.of();
        Set<String> postIds = new HashSet<>(posts.stream().map(Post::getId).toList());
        Map<String, Post> postsById = new LinkedHashMap<>();
        posts.forEach(post -> postsById.put(post.getId(), post));
        List<Map<String, String>> accepted = new ArrayList<>();
        List<Map<String, String>> rejected = new ArrayList<>();
        List<Post> updatedPosts = new ArrayList<>();

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> value)) {
                rejected.add(Map.of("reason", "结果项格式错误"));
                continue;
            }
            String postId = Objects.toString(value.get("postId"), "").trim();
            String riskLevel = normalizeRiskLevel(value.get("riskLevel"));
            if (postId.isBlank() || !postIds.contains(postId)) {
                rejected.add(Map.of("postId", postId, "reason", "帖子不存在"));
            } else if (riskLevel == null) {
                rejected.add(Map.of("postId", postId, "reason", "riskLevel 只能是低、中、高"));
            } else {
                accepted.add(Map.of("postId", postId, "riskLevel", riskLevel));
                Post post = postsById.get(postId);
                post.setRiskLevel(riskLevel);
                updatedPosts.add(post);
            }
        }

        if (!updatedPosts.isEmpty()) postRepository.saveAll(updatedPosts);
        result.put("acceptedLabels", accepted);
        result.put("acceptedLabelCount", accepted.size());
        result.put("rejectedLabels", rejected);
        result.put("executed", !accepted.isEmpty() && rejected.isEmpty());
        return result;
    }

    private String normalizeRiskLevel(Object value) {
        String level = Objects.toString(value, "").trim().replace("风险", "");
        return Set.of("低", "中", "高").contains(level) ? level : null;
    }
}
