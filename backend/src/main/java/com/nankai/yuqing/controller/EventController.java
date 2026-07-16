package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import com.nankai.yuqing.service.AnalysisSettingsService;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 事件管理接口
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;
    private final PostRepository postRepository;
    private final AnalysisService analysisService;
    private final AnalysisSettingsService settingsService;

    public EventController(EventRepository eventRepository,
                           PostRepository postRepository,
                           AnalysisService analysisService,
                           AnalysisSettingsService settingsService) {
        this.eventRepository = eventRepository;
        this.postRepository = postRepository;
        this.analysisService = analysisService;
        this.settingsService = settingsService;
    }

    /**
     * 事件列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<EventEntity> events = eventRepository.findAllByOrderByRiskScoreDescCreatedAtDesc();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日");
        List<Map<String, Object>> result = new ArrayList<>();
        for (EventEntity e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("risk", e.getRisk());
            m.put("title", e.getTitle());
            m.put("category", e.getCategory());
            m.put("posts", e.getPostCount());
            // 真实计算日均增长：基于关联帖子的时间跨度
            m.put("growth", "+" + calcDailyGrowth(e.getId(), e.getPostCount()) + "/天");
            m.put("time", e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "");
            m.put("status", e.getStatus());
            result.add(m);
        }
        return result;
    }

    /**
     * 事件详情
     */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        EventEntity event = eventRepository.findById(id).orElse(null);
        if (event == null) {
            return Map.of("error", "事件不存在");
        }

        List<Post> relatedPosts = postRepository.findByEventId(id);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", event.getId());
        result.put("title", event.getTitle());
        result.put("category", event.getCategory());
        result.put("risk", event.getRisk());
        result.put("riskScore", event.getRiskScore());
        result.put("status", event.getStatus());
        result.put("summary", event.getSummary());

        // 核心指标
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("postCount", event.getPostCount());
        stats.put("affectedRange", event.getAffectedRange());
        stats.put("urgency", event.getUrgency());
        stats.put("emotion", event.getEmotionSummary());
        result.put("stats", stats);

        // 风险判断依据
        result.put("riskReasons", analysisService.getRiskReasons(event, relatedPosts));

        // 相关帖子
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Post p : relatedPosts) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("time", p.getPublishTime() != null ? p.getPublishTime().format(fmt) : "");
            pm.put("source", p.getCategoryName());
            pm.put("emotion", emotionEmoji(p.getEmotion()));
            pm.put("comments", p.getCommentCount());
            pm.put("content", p.getTitle() + " - " + (p.getContent() != null && p.getContent().length() > 60 ? p.getContent().substring(0, 60) + "..." : p.getContent()));
            posts.add(pm);
        }
        result.put("relatedPosts", posts);

        // 趋势数据（近7天）· 真实按天统计关联帖子
        List<String> days = analysisService.getLastNDays(7);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            java.time.LocalDate date = java.time.LocalDate.now().minusDays(days.size() - 1 - i);
            final java.time.LocalDate fd = date;
            int count = (int) relatedPosts.stream()
                .filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(fd))
                .count();
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("date", days.get(i));
            t.put("count", count);
            trend.add(t);
        }
        result.put("trend", trend);

        // 处置记录（示例）
        List<Map<String, Object>> actions = new ArrayList<>();
        Map<String, Object> a1 = new LinkedHashMap<>();
        a1.put("time", "管理员 · " + (event.getCreatedAt() != null ? event.getCreatedAt().format(fmt) : ""));
        a1.put("action", "事件已创建");
        a1.put("desc", "系统自动聚合识别");
        actions.add(a1);
        if (!"待研判".equals(event.getStatus())) {
            Map<String, Object> a2 = new LinkedHashMap<>();
            a2.put("time", "管理员 · 已确认");
            a2.put("action", "风险等级确认");
            a2.put("desc", "风险等级：" + event.getRisk());
            actions.add(a2);
        }
        result.put("actions", actions);

        return result;
    }

    /**
     * 更新事件状态
     */
    @PutMapping("/{id}/status")
    public Map<String, Object> updateStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        EventEntity event = eventRepository.findById(id).orElse(null);
        if (event == null) {
            return Map.of("error", "事件不存在");
        }
        String status = body.get("status");
        String risk = body.get("risk");
        if (status != null) event.setStatus(status);
        if (risk != null) {
            event.setRisk(risk);
            AnalysisSettingsService.Snapshot settings = settingsService.getSnapshot();
            event.setRiskScore("高".equals(risk)
                ? settings.highThreshold()
                : "中".equals(risk)
                    ? settings.mediumThreshold()
                    : Math.max(0, settings.mediumThreshold() - 20));
        }
        event.setUpdatedAt(java.time.LocalDateTime.now());
        eventRepository.save(event);
        return Map.of("success", true);
    }

    private String emotionEmoji(String emotion) {
        if (emotion == null) return "😐";
        return switch (emotion) {
            case "负面" -> "😡";
            case "正面" -> "😊";
            default -> "😐";
        };
    }

    /**
     * 真实计算日均增长：基于关联帖子的时间跨度
     */
    private int calcDailyGrowth(String eventId, int postCount) {
        if (postCount == 0) return 0;
        List<Post> posts = postRepository.findByEventId(eventId);
        if (posts.isEmpty()) return 1;
        // 计算帖子时间跨度（天数）
        java.time.LocalDateTime min = posts.stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .min(java.time.LocalDateTime::compareTo)
            .orElse(null);
        java.time.LocalDateTime max = posts.stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .max(java.time.LocalDateTime::compareTo)
            .orElse(null);
        if (min == null || max == null) return 1;
        long days = java.time.Duration.between(min, max).toDays() + 1;
        if (days <= 0) days = 1;
        return Math.max(1, (int) (postCount / days));
    }
}
