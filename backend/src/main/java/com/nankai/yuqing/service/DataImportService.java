package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据导入服务
 * - 增量导入帖子（去重）
 * - 数据统计
 * - 重新分析
 * - 数据清理
 */
@Service
public class DataImportService {

    private static final Logger log = LoggerFactory.getLogger(DataImportService.class);
    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final AnalysisService analysisService;

    public DataImportService(PostRepository postRepository, EventRepository eventRepository, AnalysisService analysisService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.analysisService = analysisService;
    }

    /**
     * 增量导入帖子
     * - 根据帖子ID去重（已有相同ID则跳过）
     * - 转换 Map 为 Post 实体
     * - 返回导入统计
     */
    @Transactional
    public Map<String, Object> importPosts(List<Map<String, Object>> rawData) {
        int total = rawData.size();
        int imported = 0;
        int skipped = 0;
        int errors = 0;
        Set<String> existingIds = new HashSet<>(postRepository.findAllIds());
        List<Post> newPosts = new ArrayList<>();

        log.info("开始导入 {} 条帖子数据", total);

        for (Map<String, Object> item : rawData) {
            Object rawId = item.get("id");
            if (rawId == null || String.valueOf(rawId).isBlank() || "null".equals(String.valueOf(rawId))) {
                errors++;
                continue;
            }
            String postId = String.valueOf(rawId);

            // 去重检查：已有相同ID则跳过
            if (!existingIds.add(postId)) {
                skipped++;
                continue;
            }

            // 转换为 Post 实体
            Post post = convertToPost(item);
            if (post == null) {
                errors++;
                continue;
            }

            newPosts.add(post);
            imported++;
        }

        // 导入后执行分析
        if (imported > 0) {
            postRepository.saveAll(newPosts);
            analysisService.analyzeAllPosts();
            analysisService.aggregateEvents();
        }

        log.info("导入完成: 总数={}, 新导入={}, 跳过={}, 错误={}", total, imported, skipped, errors);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    /**
     * 重新分析所有数据
     * - 重新执行 analyzeAllPosts
     * - 重新执行 aggregateEvents
     */
    @Transactional
    public void reanalyzeAll() {
        log.info("开始重新分析所有数据");

        // 重置帖子的事件关联和分析结果
        List<Post> posts = postRepository.findAll();
        for (Post post : posts) {
            post.setEventId(null);
            post.setSafetyCategory(null);
            post.setEmotion(null);
            post.setRiskScore(0);
            post.setRiskLevel("低");
            post.setLocation(null);
            post.setProblem(null);
            post.setDemand(null);
            post.setTopic(null);
            post.setClassificationConfidence(0);
            post.setAnalysisVersion(null);
        }
        postRepository.saveAll(posts);

        // 重新执行分析
        analysisService.analyzeAllPosts();
        analysisService.aggregateEvents();

        log.info("重新分析完成");
    }

    /**
     * 清空所有数据（谨慎操作）
     */
    @Transactional
    public void clearAll() {
        log.warn("开始清空所有数据");
        eventRepository.deleteAll();
        postRepository.deleteAll();
        log.warn("所有数据已清空");
    }

    /**
     * 获取数据统计信息
     * - 帖子总数
     * - 事件数量
     * - 各分类统计
     * - 情绪统计
     * - 风险等级统计
     */
    public Map<String, Object> getDataStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Post> allPosts = postRepository.findAll();

        // 帖子总数
        long totalPosts = postRepository.count();
        stats.put("totalPosts", totalPosts);

        // 事件数量
        long eventCount = eventRepository.count();
        stats.put("eventCount", eventCount);

        // 各分类统计
        List<Object[]> categoryCounts = postRepository.countByCategory();
        Map<String, Long> categoryStats = new LinkedHashMap<>();
        for (Object[] row : categoryCounts) {
            categoryStats.put((String) row[0], (Long) row[1]);
        }
        stats.put("categoryStats", categoryStats);

        // 情绪统计
        Map<String, Long> emotionStats = new LinkedHashMap<>();
        emotionStats.put("正面", 0L);
        emotionStats.put("负面", 0L);
        emotionStats.put("中性", 0L);
        for (Post post : allPosts) {
            String emotion = post.getEmotion();
            if (emotion != null && emotionStats.containsKey(emotion)) {
                emotionStats.put(emotion, emotionStats.get(emotion) + 1);
            }
        }
        stats.put("emotionStats", emotionStats);

        // 风险等级统计
        Map<String, Long> riskLevelStats = new LinkedHashMap<>();
        riskLevelStats.put("高", 0L);
        riskLevelStats.put("中", 0L);
        riskLevelStats.put("低", 0L);
        for (Post post : allPosts) {
            String level = post.getRiskLevel();
            if (level != null && riskLevelStats.containsKey(level)) {
                riskLevelStats.put(level, riskLevelStats.get(level) + 1);
            }
        }
        stats.put("riskLevelStats", riskLevelStats);

        // 平均风险评分
        double avgRiskScore = allPosts.stream()
                .mapToInt(p -> p.getRiskScore() != null ? p.getRiskScore() : 0)
                .average()
                .orElse(0.0);
        stats.put("avgRiskScore", Math.round(avgRiskScore * 100.0) / 100.0);

        // 今日新增帖子数
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        long todayPosts = postRepository.findByPublishTimeAfter(today).size();
        stats.put("todayPosts", todayPosts);

        long safetyPosts = allPosts.stream().filter(p -> p.getSafetyCategory() != null).count();
        long highConfidencePosts = allPosts.stream()
                .filter(p -> p.getSafetyCategory() != null)
                .filter(p -> p.getClassificationConfidence() != null && p.getClassificationConfidence() >= 70)
                .count();
        long missingContent = allPosts.stream()
                .filter(p -> p.getContent() == null || p.getContent().isBlank())
                .count();
        double coverage = totalPosts == 0 ? 0 : safetyPosts * 100.0 / totalPosts;
        double highConfidenceRate = safetyPosts == 0 ? 0 : highConfidencePosts * 100.0 / safetyPosts;
        stats.put("safetyPosts", safetyPosts);
        stats.put("normalPosts", Math.max(0, totalPosts - safetyPosts));
        stats.put("safetyCoverage", Math.round(coverage * 100.0) / 100.0);
        stats.put("highConfidenceRate", Math.round(highConfidenceRate * 100.0) / 100.0);
        stats.put("missingContent", missingContent);
        stats.put("analysisVersion", AnalysisService.ANALYSIS_VERSION);

        return stats;
    }

    /**
     * 将 Map 转换为 Post 实体
     * 复用 DataInitializer 的 convertToPost 逻辑
     */
    private Post convertToPost(Map<String, Object> item) {
        try {
            Post post = new Post();
            post.setId(String.valueOf(item.get("id")));
            post.setTitle((String) item.get("title"));
            post.setContent((String) item.get("content"));
            post.setAuthor((String) item.get("author"));
            post.setAuthorAvatar((String) item.get("author_avatar"));

            // 解析时间
            String timeStr = (String) item.get("publish_time");
            if (timeStr != null) {
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    post.setPublishTime(odt.toLocalDateTime());
                } catch (Exception e) {
                    post.setPublishTime(LocalDateTime.now());
                }
            }

            Object ts = item.get("publish_timestamp");
            if (ts instanceof Number) {
                post.setPublishTimestamp(((Number) ts).longValue());
            }

            post.setCategoryId((String) item.get("category_id"));
            post.setCategoryName((String) item.get("category_name"));

            post.setCommentCount(toInt(item.get("comment_count")));
            post.setLikeCount(toInt(item.get("like_count")));
            post.setViewCount(toInt(item.get("view_count")));

            Object anon = item.get("is_anonymous");
            if (anon instanceof Number) {
                post.setIsAnonymous(((Number) anon).intValue());
            }

            // 图片URL列表
            Object imgs = item.get("image_urls");
            if (imgs instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> imgList = (List<String>) imgs;
                post.setImageUrls(imgList);
                post.setImageUrlsStr(String.join(";", imgList));
            }

            return post;
        } catch (Exception e) {
            log.warn("转换帖子失败: {}", item.get("id"), e);
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return 0;
    }
}
