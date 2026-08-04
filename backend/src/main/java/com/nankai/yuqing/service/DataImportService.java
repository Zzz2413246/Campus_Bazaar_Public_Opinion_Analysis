package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
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
    private final CommentImportService commentImportService;

    public DataImportService(PostRepository postRepository,
                             EventRepository eventRepository,
                             AnalysisService analysisService,
                             CommentImportService commentImportService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.analysisService = analysisService;
        this.commentImportService = commentImportService;
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
        int updated = 0;
        int updatedRiskLabels = 0;
        int skipped = 0;
        int errors = 0;
        int duplicatesMerged = 0;
        Map<String, Post> existingPosts = postRepository.findAll().stream()
            .collect(java.util.stream.Collectors.toMap(
                Post::getId, post -> post, (left, right) -> left));
        List<Post> newPosts = new ArrayList<>();
        Set<String> newPostIds = new HashSet<>();
        Set<String> seenIds = new HashSet<>();
        Map<String, Post> changedPosts = new LinkedHashMap<>();
        Set<String> updatedIds = new HashSet<>();
        Set<String> riskUpdatedIds = new HashSet<>();
        boolean rawFieldsChanged = false;

        log.info("开始导入 {} 条帖子数据", total);

        for (Map<String, Object> item : rawData) {
            Object rawId = item.get("id");
            if (rawId == null || String.valueOf(rawId).isBlank() || "null".equals(String.valueOf(rawId))) {
                errors++;
                continue;
            }
            String postId = String.valueOf(rawId);
            if (!seenIds.add(postId)) duplicatesMerged++;

            // 已有帖子进行非破坏性增量合并；缺失字段不会清空旧值，人工复核字段不会被导入覆盖。
            Post existing = existingPosts.get(postId);
            if (existing != null) {
                MergeResult merge = mergeRawFields(existing, item);
                if (merge.changed()) {
                    if (!newPostIds.contains(postId)) {
                        changedPosts.put(postId, existing);
                        if (updatedIds.add(postId)) updated++;
                    }
                    if (merge.rawFieldsChanged()) rawFieldsChanged = true;
                    if (merge.riskLabelChanged() && !newPostIds.contains(postId)
                        && riskUpdatedIds.add(postId)) updatedRiskLabels++;
                } else {
                    skipped++;
                }
                continue;
            }

            // 转换为 Post 实体
            Post post = convertToPost(item);
            if (post == null) {
                errors++;
                continue;
            }

            newPosts.add(post);
            newPostIds.add(postId);
            existingPosts.put(postId, post);
            imported++;
        }

        // 导入后执行分析
        if (imported > 0 || updated > 0 || updatedRiskLabels > 0) {
            if (!newPosts.isEmpty()) postRepository.saveAll(newPosts);
            if (!changedPosts.isEmpty()) postRepository.saveAll(new ArrayList<>(changedPosts.values()));
        }
        if (imported > 0 || rawFieldsChanged) {
            analysisService.analyzeAllPosts();
        }
        if (imported > 0 || updated > 0 || updatedRiskLabels > 0) {
            analysisService.aggregateEvents();
        }

        log.info("导入完成: 总数={}, 新导入={}, 跳过={}, 错误={}", total, imported, skipped, errors);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("imported", imported);
        result.put("updated", updated);
        result.put("updatedRiskLabels", updatedRiskLabels);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("duplicatesMerged", duplicatesMerged);
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
            post.setAnalyzedCommentCount(0);
            post.setNegativeCommentCount(0);
            post.setCommentSafetyCount(0);
            post.setCommentRiskAdjustment(0);
            post.setCommentSignal(null);
            post.setCommentSuggestedCategory(null);
            post.setCommentSuggestionCount(0);
            post.setAnalysisBasis(null);
        }
        postRepository.saveAll(posts);

        // 重新执行分析
        analysisService.analyzeAllPosts();
        analysisService.aggregateEvents();

        log.info("重新分析完成");
    }

    /** 外部最终分类模式下仅刷新事件关系，不覆盖已导入的分类与人工复核结果。 */
    @Transactional
    public void refreshEventAggregates() {
        log.info("外部最终分类模式：刷新事件聚合，不重算帖子分类");
        analysisService.aggregateEvents();
    }

    /**
     * 清空所有数据（谨慎操作）
     */
    @Transactional
    public void clearAll() {
        log.warn("开始清空所有数据");
        eventRepository.deleteAll();
        commentImportService.clearAll();
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
            String level = effectiveRiskLevel(post);
            if (level != null && riskLevelStats.containsKey(level)) {
                riskLevelStats.put(level, riskLevelStats.get(level) + 1);
            }
        }
        stats.put("riskLevelStats", riskLevelStats);

        long mediumHighRiskPosts = allPosts.stream()
            .map(this::effectiveRiskLevel)
            .filter(level -> "中".equals(level) || "高".equals(level))
            .count();
        stats.put("mediumHighRiskPosts", mediumHighRiskPosts);

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
        Map<String, Object> commentStats = commentImportService.getStats();
        stats.put("commentStats", commentStats);
        stats.put("quality", buildDataQuality(allPosts, commentStats));

        return stats;
    }

    private Map<String, Object> buildDataQuality(List<Post> posts, Map<String, Object> commentStats) {
        long total = posts.size();
        long missingContent = posts.stream().filter(p -> p.getContent() == null || p.getContent().isBlank()).count();
        long missingCoreText = posts.stream()
            .filter(p -> (p.getTitle() == null || p.getTitle().isBlank())
                && (p.getContent() == null || p.getContent().isBlank()))
            .count();
        long missingPublishTime = posts.stream().filter(p -> p.getPublishTime() == null).count();
        long unanalyzed = posts.stream()
            .filter(p -> p.getAnalysisVersion() == null || !AnalysisService.ANALYSIS_VERSION.equals(p.getAnalysisVersion()))
            .count();
        long invalidRisk = posts.stream()
            .map(this::effectiveRiskLevel)
            .filter(level -> level == null || !Set.of("低", "中", "高").contains(level))
            .count();
        long unmatchedComments = number(commentStats.get("unmatchedComments"));
        long totalComments = number(commentStats.get("totalComments"));
        LocalDateTime latestDataAt = posts.stream().map(Post::getPublishTime).filter(Objects::nonNull)
            .max(LocalDateTime::compareTo).orElse(null);
        long ageHours = latestDataAt == null ? -1 : Math.max(0, Duration.between(latestDataAt, LocalDateTime.now()).toHours());
        boolean stale = latestDataAt == null || ageHours > 48;

        double completeness = total == 0 ? 0 : (total - missingContent) * 100.0 / total;
        double analysisCoverage = total == 0 ? 0 : (total - unanalyzed) * 100.0 / total;
        double linkage = totalComments == 0 ? 100 : (totalComments - unmatchedComments) * 100.0 / totalComments;
        double timeliness = latestDataAt == null ? 0 : ageHours <= 24 ? 100 : ageHours <= 48 ? 80 : 40;
        int score = (int) Math.round(completeness * 0.35 + analysisCoverage * 0.30 + linkage * 0.20 + timeliness * 0.15);
        String status = score >= 90 ? "良好" : score >= 75 ? "需关注" : "需处理";

        List<Map<String, Object>> issues = new ArrayList<>();
        addQualityIssue(issues, "MISSING_CONTENT", "正文缺失", missingContent,
            missingContent > total * 0.1 ? "高" : "中", "导入时补充正文；无法补充时保留标题并标记数据来源");
        addQualityIssue(issues, "MISSING_CORE_TEXT", "标题与正文同时缺失", missingCoreText,
            "高", "检查原始抓取记录，无法恢复的数据不应进入人工研判队列");
        addQualityIssue(issues, "MISSING_TIME", "发布时间缺失", missingPublishTime,
            "高", "修复时间字段后重新导入，避免影响趋势和报告周期统计");
        addQualityIssue(issues, "UNANALYZED", "分析版本未更新", unanalyzed,
            "中", "执行重新分析，使帖子使用当前规则版本");
        addQualityIssue(issues, "INVALID_RISK", "风险标签不规范", invalidRisk,
            "高", "风险标签只允许低、中、高三个离散值");
        addQualityIssue(issues, "UNMATCHED_COMMENTS", "评论未匹配帖子", unmatchedComments,
            unmatchedComments > 500 ? "中" : "低", "核对评论 thread_id 与帖子 id，未匹配评论不参与研判");
        if (stale) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("code", "STALE_DATA");
            issue.put("name", "数据更新滞后");
            issue.put("count", 1);
            issue.put("severity", ageHours > 168 || latestDataAt == null ? "高" : "中");
            issue.put("description", latestDataAt == null ? "当前没有有效发布时间" : "最新数据距今 " + ageHours + " 小时");
            issue.put("suggestion", "检查采集或增量导入任务是否正常运行");
            issues.add(issue);
        }

        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("completeness", round(completeness));
        dimensions.put("analysisCoverage", round(analysisCoverage));
        dimensions.put("commentLinkage", round(linkage));
        dimensions.put("timeliness", round(timeliness));

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("score", score);
        quality.put("status", status);
        quality.put("generatedAt", LocalDateTime.now());
        quality.put("latestDataAt", latestDataAt);
        quality.put("ageHours", ageHours);
        quality.put("dimensions", dimensions);
        quality.put("issueCount", issues.size());
        quality.put("issues", issues);
        return quality;
    }

    private void addQualityIssue(List<Map<String, Object>> issues, String code, String name,
                                 long count, String severity, String suggestion) {
        if (count <= 0) return;
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("code", code);
        issue.put("name", name);
        issue.put("count", count);
        issue.put("severity", severity);
        issue.put("description", "当前检测到 " + count + " 条");
        issue.put("suggestion", suggestion);
        issues.add(issue);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
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
            LocalDateTime publishTime = parseTime(firstValue(item, "publish_time", "publishTime"));
            post.setPublishTime(publishTime);

            Object ts = item.get("publish_timestamp");
            if (ts instanceof Number) {
                post.setPublishTimestamp(((Number) ts).longValue());
            }

            post.setCategoryId((String) item.get("category_id"));
            post.setCategoryName((String) item.get("category_name"));

            post.setCommentCount(toInt(item.get("comment_count")));
            post.setLikeCount(toInt(item.get("like_count")));
            post.setViewCount(toInt(item.get("view_count")));
            post.setProvidedRiskLevel(normalizeRiskLabel(firstValue(
                item, "ai_risk_level", "risk_level", "riskLevel", "risk_label")));

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

    private MergeResult mergeRawFields(Post post, Map<String, Object> item) {
        boolean rawChanged = false;
        rawChanged |= mergeText(item, post.getTitle(), post::setTitle, "title");
        rawChanged |= mergeText(item, post.getContent(), post::setContent, "content");
        rawChanged |= mergeText(item, post.getAuthor(), post::setAuthor, "author");
        rawChanged |= mergeText(item, post.getAuthorAvatar(), post::setAuthorAvatar, "author_avatar", "authorAvatar");
        rawChanged |= mergeText(item, post.getCategoryId(), post::setCategoryId, "category_id", "categoryId");
        rawChanged |= mergeText(item, post.getCategoryName(), post::setCategoryName, "category_name", "categoryName");

        Object rawTime = firstPresent(item, "publish_time", "publishTime");
        if (rawTime != null) {
            LocalDateTime value = parseTime(rawTime);
            if (value != null && !Objects.equals(value, post.getPublishTime())) {
                post.setPublishTime(value);
                rawChanged = true;
            }
        }
        rawChanged |= mergeLong(item, post.getPublishTimestamp(), post::setPublishTimestamp,
            "publish_timestamp", "publishTimestamp");
        rawChanged |= mergeInt(item, post.getCommentCount(), post::setCommentCount,
            "comment_count", "commentCount");
        rawChanged |= mergeInt(item, post.getLikeCount(), post::setLikeCount,
            "like_count", "likeCount");
        rawChanged |= mergeInt(item, post.getViewCount(), post::setViewCount,
            "view_count", "viewCount");
        rawChanged |= mergeInt(item, post.getIsAnonymous(), post::setIsAnonymous,
            "is_anonymous", "isAnonymous");

        Object rawImages = firstPresent(item, "image_urls", "imageUrls");
        if (rawImages instanceof List<?> values) {
            List<String> images = values.stream()
                .filter(Objects::nonNull).map(String::valueOf).filter(value -> !value.isBlank()).toList();
            String joined = String.join(";", images);
            if (!Objects.equals(joined, Objects.toString(post.getImageUrlsStr(), ""))) {
                post.setImageUrls(images);
                post.setImageUrlsStr(joined);
                rawChanged = true;
            }
        }

        String incomingRiskLabel = normalizeRiskLabel(firstValue(
            item, "ai_risk_level", "risk_level", "riskLevel", "risk_label"));
        boolean riskChanged = incomingRiskLabel != null
            && !incomingRiskLabel.equals(post.getProvidedRiskLevel());
        if (riskChanged) post.setProvidedRiskLevel(incomingRiskLabel);
        return new MergeResult(rawChanged, riskChanged);
    }

    private boolean mergeText(Map<String, Object> item, String current,
                              java.util.function.Consumer<String> setter, String... keys) {
        Object raw = firstPresent(item, keys);
        if (raw == null) return false;
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank() || Objects.equals(value, current)) return false;
        setter.accept(value);
        return true;
    }

    private boolean mergeInt(Map<String, Object> item, Integer current,
                             java.util.function.Consumer<Integer> setter, String... keys) {
        Object raw = firstPresent(item, keys);
        Integer value = nullableInt(raw);
        if (value == null || value < 0 || Objects.equals(value, current)) return false;
        setter.accept(value);
        return true;
    }

    private boolean mergeLong(Map<String, Object> item, Long current,
                              java.util.function.Consumer<Long> setter, String... keys) {
        Object raw = firstPresent(item, keys);
        Long value = nullableLong(raw);
        if (value == null || value < 0 || Objects.equals(value, current)) return false;
        setter.accept(value);
        return true;
    }

    private LocalDateTime parseTime(Object raw) {
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private Object firstPresent(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            if (item.containsKey(key)) return item.get(key);
        }
        return null;
    }

    private Integer nullableInt(Object obj) {
        if (obj instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(Objects.toString(obj, "").trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long nullableLong(Object obj) {
        if (obj instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(Objects.toString(obj, "").trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record MergeResult(boolean rawFieldsChanged, boolean riskLabelChanged) {
        boolean changed() {
            return rawFieldsChanged || riskLabelChanged;
        }
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return 0;
    }

    private Object firstValue(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !Objects.toString(value, "").isBlank()) return value;
        }
        return null;
    }

    private String normalizeRiskLabel(Object value) {
        String label = Objects.toString(value, "").trim();
        if (label.endsWith("风险")) label = label.substring(0, label.length() - 2);
        return Set.of("低", "中", "高").contains(label) ? label : null;
    }

    private String effectiveRiskLevel(Post post) {
        if (post.getReviewedRiskLevel() != null) return post.getReviewedRiskLevel();
        if (post.getProvidedRiskLevel() != null) return post.getProvidedRiskLevel();
        return post.getRiskLevel();
    }
}
