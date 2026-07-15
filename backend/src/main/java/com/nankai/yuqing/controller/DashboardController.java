package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 舆情总览接口
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final AnalysisService analysisService;

    public DashboardController(PostRepository postRepository, EventRepository eventRepository, AnalysisService analysisService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.analysisService = analysisService;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 统计卡片
        result.put("stats", buildStats());

        // 最近事件
        result.put("recentEvents", buildRecentEvents());

        // 趋势数据
        result.put("trendData", buildTrendData());

        // 议题分布
        result.put("categoryDistribution", buildCategoryDistribution());

        // 实时风险预警
        result.put("alerts", buildAlerts());

        return result;
    }

    private List<Map<String, Object>> buildStats() {
        List<Post> allPosts = postRepository.findAll();
        long totalPosts = allPosts.size();

        // 事件统计
        List<EventEntity> events = eventRepository.findAll();
        long eventCount = events.size();
        long highRiskCount = events.stream().filter(e -> "高".equals(e.getRisk())).count();

        // 情绪统计
        long negCount = allPosts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        long posCount = allPosts.stream().filter(p -> "正面".equals(p.getEmotion())).count();
        String emotionStatus = negCount > posCount ? "偏负面" : posCount > negCount ? "偏正面" : "平稳";

        // 真实按天统计（近7天）
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        long todayPosts = allPosts.stream().filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(today)).count();
        long yesterdayPosts = allPosts.stream().filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(yesterday)).count();

        // 帖子数变化
        String postChange = calcChange(todayPosts, yesterdayPosts, true);
        // 事件数变化（事件按创建日期）
        long todayEvents = events.stream().filter(e -> e.getCreatedAt() != null && e.getCreatedAt().toLocalDate().equals(today)).count();
        long yesterdayEvents = events.stream().filter(e -> e.getCreatedAt() != null && e.getCreatedAt().toLocalDate().equals(yesterday)).count();
        String eventChange = calcChange(todayEvents, yesterdayEvents, false);

        // sparkline 真实按天帖子数
        List<Integer> postSpark = dailyCount(allPosts, 7, true);
        List<Integer> eventSpark = dailyEventCount(events, 7);
        List<Integer> highRiskSpark = dailyEventCount(events.stream().filter(e -> "高".equals(e.getRisk())).toList(), 7);
        // 情绪 sparkline：每天负面占比
        List<Integer> emotionSpark = dailyNegRatio(allPosts, 7);

        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(stat("总帖子数", totalPosts, postChange, todayPosts >= yesterdayPosts ? "up" : "good", "message-square", "brand", postSpark));
        stats.add(stat("安全事件", eventCount, eventChange, todayEvents <= yesterdayEvents ? "good" : "up", "siren", "amber", eventSpark));
        stats.add(stat("高风险事件", highRiskCount, highRiskCount > 0 ? "需关注" : "正常", highRiskCount > 0 ? "warn" : "good", "alert-triangle", "rose", highRiskSpark));
        stats.add(statText("整体情绪", emotionStatus, "负面占比" + (totalPosts > 0 ? negCount*100/totalPosts : 0) + "%", negCount > posCount ? "warn" : "up", "smile", "emerald", emotionSpark));
        return stats;
    }

    /** 计算环比变化文案 */
    private String calcChange(long today, long yesterday, boolean isPost) {
        if (yesterday == 0) {
            return today > 0 ? "较昨日新增 " + today + " 条" : "较昨日无变化";
        }
        long diff = today - yesterday;
        int pct = (int) Math.abs(diff * 100 / yesterday);
        if (diff > 0) return "较昨日 +" + pct + "%";
        if (diff < 0) return "较昨日 -" + pct + "%";
        return "较昨日持平";
    }

    /** 真实按天统计帖子数 */
    private List<Integer> dailyCount(List<Post> posts, int n, boolean byPublishTime) {
        List<Integer> data = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            int count = (int) posts.stream()
                .filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(date))
                .count();
            data.add(count);
        }
        return data;
    }

    /** 真实按天统计事件数 */
    private List<Integer> dailyEventCount(List<EventEntity> events, int n) {
        List<Integer> data = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            int count = (int) events.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().toLocalDate().equals(date))
                .count();
            data.add(count);
        }
        return data;
    }

    /** 真实按天统计负面情绪占比 */
    private List<Integer> dailyNegRatio(List<Post> posts, int n) {
        List<Integer> data = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            List<Post> dayPosts = posts.stream()
                .filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(date))
                .toList();
            int total = dayPosts.size();
            if (total == 0) {
                data.add(0);
            } else {
                long neg = dayPosts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
                data.add((int) (neg * 100 / total));
            }
        }
        return data;
    }

    private Map<String, Object> stat(String label, Object value, String change, String trend, String icon, String accent, List<Integer> spark) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("change", change);
        m.put("trend", trend);
        m.put("icon", icon);
        m.put("accent", accent);
        m.put("spark", spark);
        m.put("sparkColor", accentColor(accent));
        return m;
    }

    private Map<String, Object> statText(String label, String textValue, String change, String trend, String icon, String accent, List<Integer> spark) {
        Map<String, Object> m = stat(label, 0, change, trend, icon, accent, spark);
        m.put("textValue", textValue);
        return m;
    }

    private String accentColor(String accent) {
        return switch (accent) {
            case "brand" -> "#6366f1";
            case "amber" -> "#f59e0b";
            case "rose" -> "#f43f5e";
            case "emerald" -> "#10b981";
            default -> "#6366f1";
        };
    }

    private List<Map<String, Object>> buildRecentEvents() {
        List<EventEntity> events = eventRepository.findAllByOrderByRiskScoreDescCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日");
        for (int i = 0; i < Math.min(5, events.size()); i++) {
            EventEntity e = events.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "");
            m.put("title", e.getTitle());
            m.put("category", e.getCategory());
            m.put("risk", e.getRisk());
            m.put("status", e.getStatus());
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> buildTrendData() {
        List<String> days = analysisService.getLastNDays(7);
        List<Post> posts = postRepository.findAll();
        List<EventEntity> events = eventRepository.findAll();
        List<EventEntity> highRiskEvents = events.stream().filter(e -> "高".equals(e.getRisk())).toList();

        // 真实按天统计
        List<Integer> postSeries = dailyCount(posts, 7, true);
        List<Integer> eventSeries = dailyEventCount(events, 7);
        List<Integer> highRiskSeries = dailyEventCount(highRiskEvents, 7);

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("days", days);
        trend.put("posts", postSeries);
        trend.put("events", eventSeries);
        trend.put("highRisk", highRiskSeries);
        return trend;
    }

    private List<Map<String, Object>> buildCategoryDistribution() {
        List<Object[]> raw = postRepository.countByCategory();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", row[0]);
            m.put("value", row[1]);
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> buildAlerts() {
        List<EventEntity> events = eventRepository.findAllByOrderByRiskScoreDescCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日");
        for (int i = 0; i < Math.min(3, events.size()); i++) {
            EventEntity e = events.get(i);
            if (!"高".equals(e.getRisk()) && !"中".equals(e.getRisk())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", e.getTitle());
            m.put("risk", e.getRisk());
            m.put("postCount", e.getPostCount());
            m.put("date", e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "");
            result.add(m);
        }
        return result;
    }
}
