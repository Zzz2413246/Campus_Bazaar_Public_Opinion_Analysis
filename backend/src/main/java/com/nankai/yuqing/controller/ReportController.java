package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        // 始终展示最近 7 个自然日；无帖子的日期保留日报，各项为 0。
        List<Map<String, Object>> reports = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            reports.add(periodSummary("daily", date, date, posts, events));
        }
        return reports;
    }

    private List<Map<String, Object>> weeklyReports(List<Post> posts, List<EventEntity> events) {
        return posts.stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .map(time -> time.toLocalDate().with(DayOfWeek.MONDAY))
            .distinct()
            .sorted(Comparator.reverseOrder())
            .limit(6)
            .map(start -> periodSummary("weekly", start, start.plusDays(6), posts, events))
            .toList();
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
            .filter(post -> effectiveCategory(post) != null)
            .collect(Collectors.groupingBy(
                this::effectiveCategory, LinkedHashMap::new, Collectors.counting()));
        long positive = periodPosts.stream().filter(p -> "正面".equals(effectiveEmotion(p))).count();
        long neutral = periodPosts.stream().filter(p -> "中性".equals(effectiveEmotion(p))).count();
        long negative = periodPosts.stream().filter(p -> "负面".equals(effectiveEmotion(p))).count();
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
        result.put("meta", reportMeta(type, start.toString()));
        result.put("periodLabel", start.equals(end)
            ? start.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
            : start.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                + " 至 " + end.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")));
        result.put("overview", Map.of(
            "postCount", periodPosts.size(),
            "eventCount", periodEvents.size(),
            "highRiskCount", periodEvents.stream().filter(e -> "高".equals(e.getRisk())).count(),
            "negativeCount", negative));
        result.put("overviewText", (type.equals("weekly") ? "本周" : "本日")
            + "共监测到校园讨论 " + periodPosts.size()
            + " 条，识别安全相关事件 " + periodEvents.size()
            + " 个，其中高风险事件 "
            + periodEvents.stream().filter(e -> "高".equals(e.getRisk())).count() + " 个。");
        result.put("keyEvents", periodEvents.stream()
            .sorted(Comparator
                .comparingInt(this::eventRiskRank).reversed()
                .thenComparing(EventEntity::getPostCount,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(8)
            .map(this::eventReportItem)
            .toList());
        result.put("recommendations", periodEvents.stream().anyMatch(e -> "高".equals(e.getRisk()))
            ? List.of("立即核实高风险事件并明确责任人和完成时限。",
                "持续跟踪关联讨论和评论变化，必要时升级处置。",
                "处置完成后补充结论并形成复盘记录。")
            : List.of("整体舆情较为平稳，建议保持日常监测。",
                "关注讨论量和负面情绪的异常变化。"));
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
        DateTimeFormatter discussionTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        posts.stream().limit(10).forEach(post -> content.append("- ")
            .append(post.getPublishTime() == null ? "[时间待核实] "
                : "[" + post.getPublishTime().format(discussionTime) + "] ")
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
        result.put("meta", reportMeta("event", eventId));
        result.put("periodLabel", event.getCreatedAt() == null
            ? "时间待核实"
            : event.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")));
        result.put("overview", Map.of(
            "postCount", number(event.getPostCount()),
            "eventCount", 1,
            "highRiskCount", "高".equals(event.getRisk()) ? 1 : 0,
            "negativeCount", posts.stream().filter(post -> "负面".equals(effectiveEmotion(post))).count()));
        result.put("overviewText", Objects.toString(event.getSummary(), "暂无事件摘要"));
        result.put("categories", Map.of(
            Objects.toString(event.getCategory(), "其他风险"), (long) Math.max(1, number(event.getPostCount()))));
        result.put("emotion", Map.of(
            "positive", posts.stream().filter(post -> "正面".equals(effectiveEmotion(post))).count(),
            "neutral", posts.stream().filter(post -> "中性".equals(effectiveEmotion(post))).count(),
            "negative", posts.stream().filter(post -> "负面".equals(effectiveEmotion(post))).count()));
        result.put("keyEvents", List.of(eventReportItem(event)));
        result.put("relatedDiscussions", posts.stream().limit(10).map(post -> {
            Map<String, Object> discussion = new LinkedHashMap<>();
            discussion.put("id", post.getId());
            discussion.put("title", Objects.toString(post.getTitle(),
                Objects.toString(post.getContent(), "无标题帖子")));
            discussion.put("publishTime", post.getPublishTime() == null
                ? "" : post.getPublishTime().format(discussionTime));
            discussion.put("publishTimestamp", post.getPublishTimestamp());
            discussion.put("risk", effectiveRisk(post));
            discussion.put("emotion", Objects.toString(effectiveEmotion(post), "中性"));
            return discussion;
        }).toList());
        result.put("recommendations", "高".equals(event.getRisk())
            ? List.of("立即核实事件事实并明确牵头负责人。",
                "持续跟踪关联帖子与评论变化。",
                "完成处置后记录结论并归档。")
            : List.of("保持关注，根据后续讨论变化及时研判。",
                "如风险升级，及时调整处置优先级。"));
        return result;
    }

    private Map<String, Object> reportMeta(String type, String key) {
        Map<String, Object> meta = new LinkedHashMap<>();
        String typeCode = switch (type) {
            case "weekly" -> "ZB";
            case "event" -> "SJ";
            default -> "RB";
        };
        meta.put("reportNumber", "YQ-" + typeCode + "-" + key.replaceAll("[^0-9A-Za-z]", ""));
        meta.put("institution", "校园安全舆情分析平台");
        meta.put("confidentiality", "内部资料");
        meta.put("generatedAt", LocalDateTime.now());
        meta.put("typeLabel", switch (type) {
            case "weekly" -> "校园安全舆情周报";
            case "event" -> "校园安全事件简报";
            default -> "校园安全舆情日报";
        });
        meta.put("dataBasis", "校园集市帖子及其关联评论");
        return meta;
    }

    private Map<String, Object> eventReportItem(EventEntity event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", event.getId());
        item.put("title", event.getTitle());
        item.put("category", Objects.toString(event.getCategory(), "其他风险"));
        item.put("risk", Objects.toString(event.getRisk(), "低"));
        item.put("postCount", number(event.getPostCount()));
        item.put("status", normalizeEventStatus(event.getStatus()));
        item.put("assignee", Objects.toString(event.getAssignee(), "待指派"));
        item.put("summary", Objects.toString(event.getSummary(), "暂无摘要"));
        return item;
    }

    private int eventRiskRank(EventEntity event) {
        return switch (Objects.toString(event.getRisk(), "低")) {
            case "高" -> 3;
            case "中" -> 2;
            default -> 1;
        };
    }

    private String normalizeEventStatus(String status) {
        if (status == null || status.isBlank() || "未处理".equals(status) || "待研判".equals(status)) return "待核实";
        if ("已确认".equals(status)) return "持续观察";
        if ("已忽略".equals(status)) return "误报";
        return status;
    }

    private int number(Integer value) {
        return value == null ? 0 : value;
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

    private String effectiveCategory(Post post) {
        return post.getReviewedCategory() != null
            ? post.getReviewedCategory() : post.getSafetyCategory();
    }

    private String effectiveEmotion(Post post) {
        return post.getReviewedEmotion() != null
            ? post.getReviewedEmotion() : post.getEmotion();
    }

    private String effectiveRisk(Post post) {
        if (post.getReviewedRiskLevel() != null) return post.getReviewedRiskLevel();
        if (post.getProvidedRiskLevel() != null) return post.getProvidedRiskLevel();
        return Objects.toString(post.getRiskLevel(), "低");
    }
}
