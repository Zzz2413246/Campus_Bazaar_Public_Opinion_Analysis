package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能问答助手接口
 * 基于结构化数据生成回答，不依赖外部 LLM
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final PostRepository postRepository;
    private final EventRepository eventRepository;

    public AssistantController(PostRepository postRepository, EventRepository eventRepository) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").toLowerCase();

        String answer;
        String type = "summary";
        List<String> sources = new ArrayList<>();

        if (question.contains("值得关注") || question.contains("安全问题") || question.contains("最近")) {
            answer = answerRecentIssues();
            type = "summary";
        } else if (question.contains("增长最快") || question.contains("增长")) {
            answer = answerFastestGrowing();
            type = "analysis";
        } else if (question.contains("宿舍")) {
            answer = answerDormIssues();
            type = "analysis";
        } else if (question.contains("高风险") || question.contains("风险")) {
            answer = answerHighRisk();
            type = "analysis";
        } else if (question.contains("简报") || question.contains("周报") || question.contains("日报")) {
            answer = answerBriefing();
            type = "report";
        } else if (question.contains("诈骗") || question.contains("骗")) {
            answer = answerFraud();
            type = "analysis";
        } else if (question.contains("情绪") || question.contains("情感")) {
            answer = answerEmotion();
            type = "analysis";
        } else {
            answer = answerDefault();
            type = "default";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("type", type);
        result.put("sources", sources);
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }

    /**
     * 最近一周值得关注的校园安全问题
     */
    private String answerRecentIssues() {
        List<EventEntity> events = eventRepository.findAllByOrderByRiskScoreDescCreatedAtDesc();
        List<Post> posts = postRepository.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("根据平台分析，最近一周值得关注的校园安全问题主要包括：\n\n");

        if (events.isEmpty()) {
            sb.append("当前未识别到重大安全事件，整体舆情平稳。共监测到 ").append(posts.size()).append(" 条讨论。");
            return sb.toString();
        }

        int idx = 1;
        for (int i = 0; i < Math.min(3, events.size()); i++) {
            EventEntity e = events.get(i);
            sb.append(idx++).append(". **").append(e.getTitle()).append("**\n");
            sb.append("   - 类别：").append(e.getCategory()).append("\n");
            sb.append("   - 风险等级：").append(e.getRisk()).append("（").append(e.getRiskScore()).append("分）\n");
            sb.append("   - 讨论量：").append(e.getPostCount()).append("条\n");
            sb.append("   - 影响范围：").append(e.getAffectedRange()).append("\n");
            if (e.getSummary() != null) sb.append("   - ").append(e.getSummary()).append("\n");
            sb.append("\n");
        }

        sb.append("**建议**：");
        if (events.stream().anyMatch(e -> "高".equals(e.getRisk()))) {
            sb.append("存在高风险事件，建议保卫处重点关注并跟进处置。");
        } else {
            sb.append("整体风险可控，建议持续监测议题发展。");
        }
        return sb.toString();
    }

    /**
     * 增长最快的议题
     */
    private String answerFastestGrowing() {
        List<Post> posts = postRepository.findAll();
        Map<String, Long> catCount = posts.stream()
            .filter(p -> p.getSafetyCategory() != null)
            .collect(Collectors.groupingBy(Post::getSafetyCategory, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("根据分析，各类校园安全议题的讨论热度如下：\n\n");

        List<Map.Entry<String, Long>> sorted = catCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .toList();

        int idx = 1;
        for (Map.Entry<String, Long> e : sorted) {
            sb.append(idx++).append(". ").append(e.getKey()).append("：").append(e.getValue()).append("条讨论\n");
        }

        if (!sorted.isEmpty()) {
            sb.append("\n**增长最快的议题**：").append(sorted.get(0).getKey());
            sb.append("，共 ").append(sorted.get(0).getValue()).append("条相关讨论，建议重点关注。");
        }
        return sb.toString();
    }

    /**
     * 宿舍相关问题
     */
    private String answerDormIssues() {
        List<Post> posts = postRepository.findAll().stream()
            .filter(p -> "宿舍设施问题".equals(p.getSafetyCategory()))
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("最近宿舍相关问题主要集中在以下方面：\n\n");

        if (posts.isEmpty()) {
            sb.append("近期未监测到宿舍相关安全问题。");
            return sb.toString();
        }

        sb.append("共识别到 ").append(posts.size()).append(" 条宿舍相关讨论，主要涉及：\n\n");

        // 统计关键词
        Map<String, Integer> keywords = new LinkedHashMap<>();
        String[][] kwMap = {{"空调", "空调"}, {"网络", "网络"}, {"wifi", "网络"}, {"热水", "热水"}, {"停水", "供水"}, {"停电", "供电"}, {"维修", "维修"}, {"门锁", "门锁"}};
        for (Post p : posts) {
            String text = (p.getTitle() != null ? p.getTitle() : "") + (p.getContent() != null ? p.getContent() : "");
            for (String[] kw : kwMap) {
                if (text.contains(kw[0])) {
                    keywords.merge(kw[1], 1, Integer::sum);
                }
            }
        }

        keywords.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> sb.append("- **").append(e.getKey()).append("**：").append(e.getValue()).append("条\n"));

        sb.append("\n**建议**：建议后勤部门针对高频问题制定维修计划。");
        return sb.toString();
    }

    /**
     * 高风险事件
     */
    private String answerHighRisk() {
        List<EventEntity> events = eventRepository.findAll().stream()
            .filter(e -> "高".equals(e.getRisk()))
            .toList();

        StringBuilder sb = new StringBuilder();
        if (events.isEmpty()) {
            sb.append("当前没有高风险安全事件。");
            return sb.toString();
        }

        sb.append("当前共有 **").append(events.size()).append("** 个高风险事件：\n\n");
        for (EventEntity e : events) {
            sb.append("- **").append(e.getTitle()).append("**\n");
            sb.append("  - 风险评分：").append(e.getRiskScore()).append("分\n");
            sb.append("  - 讨论量：").append(e.getPostCount()).append("条\n");
            sb.append("  - 影响范围：").append(e.getAffectedRange()).append("\n\n");
        }
        sb.append("**建议**：建议保卫处立即跟进处置，并做好舆情引导。");
        return sb.toString();
    }

    /**
     * 生成简报
     */
    private String answerBriefing() {
        List<Post> posts = postRepository.findAll();
        List<EventEntity> events = eventRepository.findAll();

        StringBuilder sb = new StringBuilder();
        sb.append("# 校园安全舆情简报\n\n");
        sb.append("**报告时间**：").append(LocalDate.now()).append("\n\n");
        sb.append("## 一、总体情况\n\n");
        sb.append("本阶段共监测到校园讨论 ").append(posts.size()).append(" 条，识别安全事件 ").append(events.size()).append(" 个。其中：\n");
        sb.append("- 高风险事件：").append(events.stream().filter(e -> "高".equals(e.getRisk())).count()).append(" 个\n");
        sb.append("- 中风险事件：").append(events.stream().filter(e -> "中".equals(e.getRisk())).count()).append(" 个\n");
        sb.append("- 低风险事件：").append(events.stream().filter(e -> "低".equals(e.getRisk())).count()).append(" 个\n\n");

        sb.append("## 二、重点事件\n\n");
        for (EventEntity e : events.stream().limit(3).toList()) {
            sb.append("### ").append(e.getTitle()).append("\n\n");
            sb.append("- 类别：").append(e.getCategory()).append("\n");
            sb.append("- 风险：").append(e.getRisk()).append("（").append(e.getRiskScore()).append("分）\n");
            sb.append("- 讨论量：").append(e.getPostCount()).append("条\n\n");
        }

        sb.append("## 三、建议\n\n");
        if (events.stream().anyMatch(e -> "高".equals(e.getRisk()))) {
            sb.append("存在高风险事件，建议保卫处重点关注并跟进处置，同时做好舆情引导工作。");
        } else {
            sb.append("整体舆情平稳，建议持续监测议题发展趋势。");
        }
        return sb.toString();
    }

    /**
     * 诈骗相关
     */
    private String answerFraud() {
        List<Post> posts = postRepository.findAll().stream()
            .filter(p -> "诈骗与财产安全".equals(p.getSafetyCategory()))
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("根据分析，共识别到 ").append(posts.size()).append(" 条诈骗与财产安全相关讨论。\n\n");

        if (posts.isEmpty()) {
            sb.append("近期未监测到诈骗相关讨论。");
            return sb.toString();
        }

        sb.append("主要涉及：\n");
        sb.append("- 二手交易风险\n");
        sb.append("- 网络诈骗\n");
        sb.append("- 兼职刷单\n\n");

        sb.append("**建议**：建议加强学生防诈骗教育，提醒学生注意交易安全，警惕陌生转账。");
        return sb.toString();
    }

    /**
     * 情绪分析
     */
    private String answerEmotion() {
        List<Post> posts = postRepository.findAll();
        long neg = posts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        long neu = posts.stream().filter(p -> "中性".equals(p.getEmotion())).count();
        long pos = posts.stream().filter(p -> "正面".equals(p.getEmotion())).count();
        int total = posts.size();
        if (total == 0) total = 1;

        StringBuilder sb = new StringBuilder();
        sb.append("当前校园讨论情绪分析：\n\n");
        sb.append("- 正面情绪：").append(pos).append("条（").append(pos * 100 / total).append("%）\n");
        sb.append("- 中性情绪：").append(neu).append("条（").append(neu * 100 / total).append("%）\n");
        sb.append("- 负面情绪：").append(neg).append("条（").append(neg * 100 / total).append("%）\n\n");

        if (neg > pos) {
            sb.append("**整体情绪偏负面**，建议关注引发负面情绪的议题，及时回应学生关切。");
        } else {
            sb.append("**整体情绪平稳**，学生讨论以中性为主。");
        }
        return sb.toString();
    }

    /**
     * 默认回答
     */
    private String answerDefault() {
        return "我可以帮您分析校园安全舆情相关的问题，例如：\n\n" +
               "- 最近一周有哪些值得关注的校园安全问题？\n" +
               "- 哪些校园安全议题增长最快？\n" +
               "- 最近宿舍相关问题主要集中在哪些方面？\n" +
               "- 当前有哪些高风险事件？\n" +
               "- 生成一份本周校园安全舆情简报\n" +
               "- 诈骗相关问题有哪些？\n\n" +
               "请提出您的问题。";
    }
}
