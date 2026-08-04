package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
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
    public Map<String, Object> dashboard(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Post> allPosts = postRepository.findAll();
        List<EventEntity> allEvents = eventRepository.findAllByOrderByRiskScoreDescCreatedAtDesc();

        // 统计卡片
        result.put("stats", buildStats(allPosts, allEvents));

        // 最近事件
        result.put("recentEvents", buildRecentEvents(allEvents));

        // 趋势数据
        result.put("trendData", buildTrendData(allPosts, allEvents, resolveTrendRange(days, startDate, endDate)));

        // 议题分布
        result.put("categoryDistribution", buildCategoryDistribution());

        // 实时风险预警
        List<Map<String, Object>> alerts = buildAlerts(allEvents);
        result.put("alerts", alerts);

        // 首页行动工作台
        result.put("workbench", buildWorkbench(allPosts, allEvents, alerts));

        return result;
    }

    private List<Map<String, Object>> buildStats(List<Post> allPosts, List<EventEntity> events) {
        long totalPosts = allPosts.size();

        // 事件统计
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
        // 事件由全量帖子聚合生成，创建时间会随重新分析刷新，不能把本次聚合误报为“今日新增”。
        String eventChange = "全量样本聚合结果";

        // sparkline 真实按天帖子数
        List<Integer> postSpark = dailyCount(allPosts, 7, true);
        List<Integer> eventSpark = dailyEventCount(events, 7);
        List<Integer> highRiskSpark = dailyEventCount(events.stream().filter(e -> "高".equals(e.getRisk())).toList(), 7);
        // 情绪 sparkline：每天负面占比
        List<Integer> emotionSpark = dailyNegRatio(allPosts, 7);

        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(stat("总帖子数", totalPosts, postChange, todayPosts >= yesterdayPosts ? "up" : "good", "message-square", "brand", postSpark));
        stats.add(stat("安全事件", eventCount, eventChange, "good", "siren", "amber", eventSpark));
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

    private List<Map<String, Object>> buildRecentEvents(List<EventEntity> events) {
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日");
        for (int i = 0; i < Math.min(5, events.size()); i++) {
            EventEntity e = events.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("time", e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "");
            m.put("title", e.getTitle());
            m.put("category", e.getCategory());
            m.put("risk", e.getRisk());
            m.put("status", e.getStatus());
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> buildTrendData(List<Post> posts, List<EventEntity> events, TrendRange range) {
        Set<String> highRiskEventIds = events.stream()
            .filter(event -> "高".equals(event.getRisk()))
            .map(EventEntity::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        List<String> labels = new ArrayList<>();
        List<Integer> postSeries = new ArrayList<>();
        List<Integer> eventSeries = new ArrayList<>();
        List<Integer> highRiskSeries = new ArrayList<>();
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            final LocalDate currentDate = date;
            List<Post> dayPosts = posts.stream()
                .filter(post -> post.getPublishTime() != null)
                .filter(post -> currentDate.equals(post.getPublishTime().toLocalDate()))
                .toList();
            Set<String> dayEventIds = dayPosts.stream()
                .map(Post::getEventId)
                .filter(id -> id != null && !id.isBlank())
                .collect(java.util.stream.Collectors.toSet());
            labels.add(date.format(DateTimeFormatter.ofPattern("M/d")));
            postSeries.add(dayPosts.size());
            // 事件在页面展示为其首次出现的帖子日期，避免重新聚合日期掩盖原始舆情发生时间。
            eventSeries.add(dayEventIds.size());
            highRiskSeries.add((int) dayEventIds.stream().filter(highRiskEventIds::contains).count());
        }

        Map<String, Object> trend = new LinkedHashMap<>();
        trend.put("labels", labels);
        trend.put("startDate", range.start());
        trend.put("endDate", range.end());
        trend.put("days", labels.size());
        trend.put("timedPosts", posts.stream().filter(post -> post.getPublishTime() != null).count());
        trend.put("totalPosts", posts.size());
        trend.put("posts", postSeries);
        trend.put("events", eventSeries);
        trend.put("highRisk", highRiskSeries);
        return trend;
    }

    /** 限定最长一年；不带参数时保持原有近 7 天行为。 */
    private TrendRange resolveTrendRange(int days, LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        LocalDate start = startDate == null ? end.minusDays(Math.max(1, Math.min(days, 366)) - 1L) : startDate;
        if (start.isAfter(end)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(start, end) > 365) start = end.minusDays(365);
        return new TrendRange(start, end);
    }

    private record TrendRange(LocalDate start, LocalDate end) {}

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

    private List<Map<String, Object>> buildAlerts(List<EventEntity> events) {
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日");
        for (EventEntity e : events) {
            if (!"高".equals(e.getRisk()) && !"中".equals(e.getRisk())) continue;
            if (Set.of("已解决", "误报", "已忽略").contains(normalizeStatus(e.getStatus()))) continue;
            List<Post> posts = postRepository.findByEventId(e.getId());
            List<Map<String, Object>> triggers =
                analysisService.getAlertTriggers(e, posts);
            if (triggers.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("title", e.getTitle());
            m.put("risk", e.getRisk());
            m.put("postCount", e.getPostCount());
            m.put("date", e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "");
            m.put("matchedRuleCount", triggers.size());
            m.put("triggerSummary", triggers.stream()
                .limit(2)
                .map(trigger -> Objects.toString(trigger.get("reason"), ""))
                .filter(reason -> !reason.isBlank())
                .collect(java.util.stream.Collectors.joining("、")));
            m.put("triggers", triggers);
            result.add(m);
            if (result.size() >= 5) break;
        }
        return result;
    }

    private Map<String, Object> buildWorkbench(
            List<Post> posts,
            List<EventEntity> events,
            List<Map<String, Object>> alerts) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate yesterday = today.minusDays(1);

        List<Post> pendingReviews = posts.stream()
            .filter(post -> post.getReviewStatus() == null || "待复核".equals(post.getReviewStatus()))
            .sorted(Comparator
                .comparingInt(this::effectiveRiskRank).reversed()
                .thenComparing(Comparator.comparingInt(this::postHeat).reversed())
                .thenComparing(Post::getPublishTime,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        List<EventEntity> openEvents = events.stream()
            .filter(event -> !Set.of("已解决", "误报", "已忽略").contains(normalizeStatus(event.getStatus())))
            .toList();
        List<EventEntity> overdueEvents = openEvents.stream()
            .filter(event -> event.getDueAt() != null && event.getDueAt().isBefore(now))
            .sorted(Comparator.comparing(EventEntity::getDueAt))
            .toList();
        List<EventEntity> dueSoonEvents = openEvents.stream()
            .filter(event -> event.getDueAt() != null)
            .filter(event -> !event.getDueAt().isBefore(now))
            .filter(event -> !event.getDueAt().isAfter(now.plusHours(48)))
            .sorted(Comparator.comparing(EventEntity::getDueAt))
            .toList();

        LocalDateTime latestDataTime = posts.stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(null);
        long dataAgeHours = latestDataTime == null
            ? -1 : Math.max(0, Duration.between(latestDataTime, now).toHours());
        boolean staleData = latestDataTime == null || dataAgeHours > 48;
        long missingContent = posts.stream()
            .filter(post -> post.getContent() == null || post.getContent().isBlank())
            .count();

        long activeAlertCount = events.stream()
            .filter(event -> ("高".equals(event.getRisk()) || "中".equals(event.getRisk())))
            .filter(event -> !Set.of("已解决", "误报", "已忽略").contains(normalizeStatus(event.getStatus())))
            .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pendingReviewCount", pendingReviews.size());
        summary.put("activeAlertCount", activeAlertCount);
        summary.put("dueSoonCount", dueSoonEvents.size());
        summary.put("overdueCount", overdueEvents.size());
        summary.put("dataIssueCount", staleData ? 1 : 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("pendingReviews", pendingReviews.stream().limit(5).map(this::pendingReviewItem).toList());
        result.put("deadlineEvents", java.util.stream.Stream.concat(
                overdueEvents.stream(), dueSoonEvents.stream())
            .distinct()
            .limit(5)
            .map(event -> deadlineItem(event, now))
            .toList());
        result.put("dailyChanges", List.of(
            dailyChange("新增帖子", countPostsOn(posts, today), countPostsOn(posts, yesterday), "条"),
            dailyChange("中高风险标签", countRiskPostsOn(posts, today), countRiskPostsOn(posts, yesterday), "条"),
            dailyChange("负面情绪占比", negativeRatioOn(posts, today), negativeRatioOn(posts, yesterday), "%")
        ));

        Map<String, Object> dataStatus = new LinkedHashMap<>();
        dataStatus.put("status", staleData ? "需检查" : "正常");
        dataStatus.put("latestDataAt", latestDataTime);
        dataStatus.put("ageHours", dataAgeHours);
        dataStatus.put("missingContentCount", missingContent);
        dataStatus.put("message", latestDataTime == null
            ? "当前没有可用的帖子发布时间"
            : staleData
                ? "最新帖子数据距今已超过48小时，请检查采集或导入任务"
                : "帖子数据在48小时内有更新");
        result.put("dataStatus", dataStatus);
        result.put("displayedAlertCount", alerts.size());
        return result;
    }

    private Map<String, Object> pendingReviewItem(Post post) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", post.getId());
        item.put("title", firstNonBlank(post.getTitle(), post.getContent(), "无标题帖子"));
        item.put("risk", effectiveRisk(post));
            item.put("category", firstNonBlank(post.getReviewedCategory(), post.getSafetyCategory(), "疑似主题无法确定"));
        item.put("time", post.getPublishTime());
        item.put("heat", postHeat(post));
        return item;
    }

    private Map<String, Object> deadlineItem(EventEntity event, LocalDateTime now) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", event.getId());
        item.put("title", event.getTitle());
        item.put("risk", event.getRisk());
        item.put("status", normalizeStatus(event.getStatus()));
        item.put("assignee", firstNonBlank(event.getAssignee(), "待指派"));
        item.put("dueAt", event.getDueAt());
        item.put("overdue", event.getDueAt() != null && event.getDueAt().isBefore(now));
        return item;
    }

    private Map<String, Object> dailyChange(String label, long today, long yesterday, String unit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("today", today);
        item.put("yesterday", yesterday);
        item.put("difference", today - yesterday);
        item.put("unit", unit);
        item.put("direction", today > yesterday ? "up" : today < yesterday ? "down" : "flat");
        return item;
    }

    private long countPostsOn(List<Post> posts, LocalDate date) {
        return posts.stream()
            .filter(post -> post.getPublishTime() != null && post.getPublishTime().toLocalDate().equals(date))
            .count();
    }

    private long countRiskPostsOn(List<Post> posts, LocalDate date) {
        return posts.stream()
            .filter(post -> post.getPublishTime() != null && post.getPublishTime().toLocalDate().equals(date))
            .filter(post -> Set.of("中", "高").contains(effectiveRisk(post)))
            .count();
    }

    private long negativeRatioOn(List<Post> posts, LocalDate date) {
        List<Post> dayPosts = posts.stream()
            .filter(post -> post.getPublishTime() != null && post.getPublishTime().toLocalDate().equals(date))
            .toList();
        if (dayPosts.isEmpty()) return 0;
        long negative = dayPosts.stream()
            .filter(post -> "负面".equals(firstNonBlank(post.getReviewedEmotion(), post.getEmotion())))
            .count();
        return negative * 100 / dayPosts.size();
    }

    private int postHeat(Post post) {
        return number(post.getViewCount()) + number(post.getCommentCount()) * 20 + number(post.getLikeCount()) * 5;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
    }

    private int effectiveRiskRank(Post post) {
        return switch (effectiveRisk(post)) {
            case "高" -> 3;
            case "中" -> 2;
            default -> 1;
        };
    }

    private String effectiveRisk(Post post) {
        return firstNonBlank(post.getReviewedRiskLevel(), post.getProvidedRiskLevel(), post.getRiskLevel(), "低");
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "未处理".equals(status) || "待研判".equals(status)) return "待核实";
        if ("已确认".equals(status)) return "持续观察";
        if ("已忽略".equals(status)) return "误报";
        return status;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
