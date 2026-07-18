package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报告中心接口：支持日报、周报和事件简报。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final PostRepository postRepository;
    private final EventRepository eventRepository;

    public ReportController(PostRepository postRepository, EventRepository eventRepository) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(defaultValue = "daily") String type) {
        List<Post> posts = postRepository.findAll();
        List<EventEntity> events = eventRepository.findAll();
        return switch (normalizeType(type)) {
            case "weekly" -> weeklyReports(posts, events);
            case "event" -> eventReports(events);
            default -> dailyReports(posts, events);
        };
    }

    @GetMapping("/{key}")
    public Map<String, Object> detail(
            @PathVariable String key,
            @RequestParam(defaultValue = "daily") String type) {
        return switch (normalizeType(type)) {
            case "weekly" -> {
                LocalDate start = LocalDate.parse(key);
                yield buildPeriodReport("weekly", start, start.plusDays(6));
            }
            case "event" -> buildEventReport(key);
            default -> {
                LocalDate date = LocalDate.parse(key);
                yield buildPeriodReport("daily", date, date);
            }
        };
    }

    private List<Map<String, Object>> dailyReports(List<Post> posts, List<EventEntity> events) {
        List<Map<String, Object>> reports = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            reports.add(periodSummary("daily", date, date, posts, events));
        }
        return reports;
    }

    private List<Map<String, Object>> weeklyReports(List<Post> posts, List<EventEntity> events) {
        List<Map<String, Object>> reports = new ArrayList<>();
        LocalDate thisMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        for (int i = 0; i < 6; i++) {
            LocalDate start = thisMonday.minusWeeks(i);
            reports.add(periodSummary("weekly", start, start.plusDays(6), posts, events));
        }
        return reports;
    }

    private List<Map<String, Object>> eventReports(List<EventEntity> events) {
        return events.stream()
            .sorted(Comparator.comparing(
                EventEntity::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(event -> {
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("key", event.getId());
                report.put("date", event.getCreatedAt() == null
                    ? "" : event.getCreatedAt().toLocalDate().toString());
                report.put("title", "事件简报 · " + event.getTitle());
                report.put("newPosts", event.getPostCount());
                report.put("events", 1);
                report.put("highRisk", "高".equals(event.getRisk()) ? 1 : 0);
                report.put("status", "已生成");
                return report;
            })
            .toList();
    }

    private Map<String, Object> periodSummary(
            String type,
            LocalDate start,
            LocalDate end,
            List<Post> posts,
            List<EventEntity> events) {
        long postCount = posts.stream().filter(post -> inRange(post, start, end)).count();
        List<EventEntity> periodEvents = events.stream()
            .filter(event -> inRange(event, start, end))
            .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", start.toString());
        result.put("date", start.toString());
        result.put("periodStart", start.toString());
        result.put("periodEnd", end.toString());
        result.put("title", periodTitle(type, start, end));
        result.put("newPosts", postCount);
        result.put("events", periodEvents.size());
        result.put("highRisk", periodEvents.stream().filter(e -> "高".equals(e.getRisk())).count());
        result.put("status", "已生成");
        return result;
    }

    private Map<String, Object> buildPeriodReport(String type, LocalDate start, LocalDate end) {
        List<Post> periodPosts = postRepository.findAll().stream()
            .filter(post -> inRange(post, start, end))
            .toList();
        List<EventEntity> periodEvents = eventRepository.findAll().stream()
            .filter(event -> inRange(event, start, end))
            .toList();

        Map<String, Long> categories = periodPosts.stream()
            .filter(post -> post.getSafetyCategory() != null)
            .collect(Collectors.groupingBy(
                Post::getSafetyCategory, LinkedHashMap::new, Collectors.counting()));
        long positive = periodPosts.stream().filter(p -> "正面".equals(p.getEmotion())).count();
        long neutral = periodPosts.stream().filter(p -> "中性".equals(p.getEmotion())).count();
        long negative = periodPosts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        String title = periodTitle(type, start, end);

        StringBuilder content = new StringBuilder();
        content.append("# ").append(title).append("\n\n");
        content.append("## 一、总体情况\n\n");
        content.append(type.equals("weekly") ? "本周" : "本日")
            .append("共监测到校园讨论 ").append(periodPosts.size())
            .append(" 条，识别安全相关事件 ").append(periodEvents.size())
            .append(" 个，其中高风险事件 ")
            .append(periodEvents.stream().filter(e -> "高".equals(e.getRisk())).count())
            .append(" 个。\n\n");
        content.append("## 二、议题分布\n\n");
        if (categories.isEmpty()) content.append("- 暂无安全议题数据\n");
        categories.forEach((category, count) ->
            content.append("- **").append(category).append("**：").append(count).append(" 条\n"));
        content.append("\n## 三、情绪分析\n\n")
            .append("- 正面：").append(positive).append(" 条\n")
            .append("- 中性：").append(neutral).append(" 条\n")
            .append("- 负面：").append(negative).append(" 条\n\n");
        appendEvents(content, periodEvents);
        content.append("\n## 五、建议\n\n");
        content.append(periodEvents.stream().anyMatch(e -> "高".equals(e.getRisk()))
            ? "存在高风险事件，建议相关部门重点关注并跟进处置。\n"
            : "整体舆情平稳，建议持续监测。\n");

        Map<String, Object> result = periodSummary(
            type, start, end, periodPosts, periodEvents);
        result.put("content", content.toString());
        result.put("categories", categories);
        result.put("emotion", Map.of(
            "positive", positive, "neutral", neutral, "negative", negative));
        return result;
    }

    private Map<String, Object> buildEventReport(String eventId) {
        EventEntity event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Map.of("error", "事件不存在");
        List<Post> posts = postRepository.findByEventId(eventId);

        String title = "事件简报 · " + event.getTitle();
        StringBuilder content = new StringBuilder();
        content.append("# ").append(title).append("\n\n")
            .append("## 一、事件概况\n\n")
            .append("- 风险等级：").append(event.getRisk()).append("\n")
            .append("- 事件类别：").append(event.getCategory()).append("\n")
            .append("- 关联帖子：").append(event.getPostCount()).append(" 条\n")
            .append("- 当前状态：").append(event.getStatus()).append("\n")
            .append("- 影响范围：").append(Objects.toString(event.getAffectedRange(), "待研判")).append("\n\n")
            .append("## 二、事件摘要\n\n")
            .append(Objects.toString(event.getSummary(), "暂无摘要")).append("\n\n")
            .append("## 三、相关讨论\n\n");
        posts.stream().limit(10).forEach(post -> content.append("- ")
            .append(Objects.toString(post.getTitle(), Objects.toString(post.getContent(), "无标题")))
            .append("\n"));
        content.append("\n## 四、处置建议\n\n")
            .append("高".equals(event.getRisk())
                ? "建议立即核实并持续跟进处置进度。\n"
                : "建议保持关注，根据后续讨论变化及时研判。\n");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", eventId);
        result.put("date", event.getCreatedAt() == null
            ? "" : event.getCreatedAt().toLocalDate().toString());
        result.put("title", title);
        result.put("content", content.toString());
        result.put("newPosts", event.getPostCount());
        result.put("events", 1);
        result.put("highRisk", "高".equals(event.getRisk()) ? 1 : 0);
        result.put("status", "已生成");
        return result;
    }

    private void appendEvents(StringBuilder content, List<EventEntity> events) {
        content.append("## 四、重点事件\n\n");
        if (events.isEmpty()) {
            content.append("本期无重大安全事件。\n");
            return;
        }
        for (EventEntity event : events) {
            content.append("- **").append(event.getTitle()).append("**（")
                .append(event.getCategory()).append("）\n")
                .append("  风险等级：").append(event.getRisk())
                .append("，讨论量：").append(event.getPostCount()).append("条\n");
        }
    }

    private String periodTitle(String type, LocalDate start, LocalDate end) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("MM月dd日");
        if ("weekly".equals(type)) {
            return start.format(day) + "—" + end.format(day) + " 校园安全周报";
        }
        return start.format(day) + " 校园安全日报";
    }

    private boolean inRange(Post post, LocalDate start, LocalDate end) {
        if (post.getPublishTime() == null) return false;
        LocalDate date = post.getPublishTime().toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private boolean inRange(EventEntity event, LocalDate start, LocalDate end) {
        if (event.getCreatedAt() == null) return false;
        LocalDate date = event.getCreatedAt().toLocalDate();
        return !date.isBefore(start) && !date.isAfter(end);
    }

    private String normalizeType(String type) {
        return Set.of("daily", "weekly", "event").contains(type) ? type : "daily";
    }
}
