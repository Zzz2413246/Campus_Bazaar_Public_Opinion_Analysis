package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 报告中心接口
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
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> reports = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // 生成最近7天的日报
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            final LocalDate fd = date;
            List<Post> dayPosts = postRepository.findAll().stream()
                .filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(fd))
                .toList();

            List<EventEntity> events = eventRepository.findAll();
            long dayEvents = events.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().toLocalDate().equals(fd))
                .count();
            long highRisk = events.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().toLocalDate().equals(fd) && "高".equals(e.getRisk()))
                .count();

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", date.format(fmt));
            m.put("title", date.format(DateTimeFormatter.ofPattern("MM月dd日")) + " 校园安全日报");
            m.put("newPosts", dayPosts.size());
            m.put("events", dayEvents);
            m.put("highRisk", highRisk);
            m.put("status", "已生成");
            reports.add(m);
        }
        return reports;
    }

    @GetMapping("/{date}")
    public Map<String, Object> detail(@PathVariable String date) {
        LocalDate ld = LocalDate.parse(date);
        final LocalDate fd = ld;
        List<Post> dayPosts = postRepository.findAll().stream()
            .filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(fd))
            .toList();

        List<EventEntity> events = eventRepository.findAll().stream()
            .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().toLocalDate().equals(fd))
            .toList();

        // 安全类别统计
        Map<String, Long> catCount = dayPosts.stream()
            .filter(p -> p.getSafetyCategory() != null)
            .collect(java.util.stream.Collectors.groupingBy(Post::getSafetyCategory, java.util.stream.Collectors.counting()));

        // 情绪统计
        long neg = dayPosts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        long pos = dayPosts.stream().filter(p -> "正面".equals(p.getEmotion())).count();
        long neu = dayPosts.stream().filter(p -> "中性".equals(p.getEmotion())).count();

        // 生成简报内容
        StringBuilder content = new StringBuilder();
        content.append("# ").append(ld.format(DateTimeFormatter.ofPattern("MM月dd日"))).append(" 校园安全舆情日报\n\n");
        content.append("## 一、总体情况\n\n");
        content.append("本日共监测到校园讨论 ").append(dayPosts.size()).append(" 条，识别安全相关事件 ").append(events.size()).append(" 个，其中高风险事件 ").append(events.stream().filter(e -> "高".equals(e.getRisk())).count()).append(" 个。\n\n");
        content.append("## 二、议题分布\n\n");
        for (Map.Entry<String, Long> e : catCount.entrySet()) {
            content.append("- **").append(e.getKey()).append("**：").append(e.getValue()).append(" 条\n");
        }
        content.append("\n## 三、情绪分析\n\n");
        content.append("- 正面：").append(pos).append(" 条\n");
        content.append("- 中性：").append(neu).append(" 条\n");
        content.append("- 负面：").append(neg).append(" 条\n\n");
        content.append("## 四、重点事件\n\n");
        if (events.isEmpty()) {
            content.append("本日无重大安全事件。\n");
        } else {
            for (EventEntity e : events) {
                content.append("- **").append(e.getTitle()).append("**（").append(e.getCategory()).append("）\n");
                content.append("  风险等级：").append(e.getRisk()).append("，讨论量：").append(e.getPostCount()).append("条\n");
                if (e.getSummary() != null) content.append("  ").append(e.getSummary()).append("\n");
            }
        }
        content.append("\n## 五、建议\n\n");
        if (events.stream().anyMatch(e -> "高".equals(e.getRisk()))) {
            content.append("存在高风险事件，建议保卫处重点关注并跟进处置。\n");
        } else {
            content.append("整体舆情平稳，建议持续监测。\n");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date);
        result.put("title", ld.format(DateTimeFormatter.ofPattern("MM月dd日")) + " 校园安全日报");
        result.put("content", content.toString());
        result.put("newPosts", dayPosts.size());
        result.put("events", events.size());
        result.put("highRisk", events.stream().filter(e -> "高".equals(e.getRisk())).count());
        result.put("categories", catCount);
        result.put("emotion", Map.of("positive", pos, "neutral", neu, "negative", neg));
        return result;
    }
}
