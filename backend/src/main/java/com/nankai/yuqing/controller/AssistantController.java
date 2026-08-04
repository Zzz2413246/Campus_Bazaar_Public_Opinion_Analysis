package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AssistantLlmClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能问答助手接口
 * 基于结构化数据生成可追溯回答；配置兼容 API 后可由 LLM 优化表达。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final AssistantLlmClient llmClient;

    public AssistantController(PostRepository postRepository,
                               EventRepository eventRepository,
                               AssistantLlmClient llmClient) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.llmClient = llmClient;
    }

    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody Map<String, Object> body) {
        String rawQuestion = Objects.toString(body.get("question"), "").trim();
        if (rawQuestion.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }
        if (rawQuestion.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能超过 1000 字");
        }
        String question = rawQuestion.toLowerCase(Locale.ROOT);
        List<Map<String, String>> history = parseHistory(body.get("history"));

        String answer;
        String type = "summary";
        List<Map<String, String>> sources = new ArrayList<>();
        List<String> followUps;

        // Specific intents must be checked before broad words such as “最近” and “风险”.
        if (question.contains("简报") || question.contains("周报") || question.contains("日报")) {
            answer = answerBriefing();
            type = "report";
            sources.add(source("报告中心", "/reports"));
            followUps = List.of("列出当前高风险事件", "本周负面情绪占比是多少？");
        } else if (question.contains("增长最快") || question.contains("增长") || question.contains("上升")) {
            answer = answerFastestGrowing();
            type = "analysis";
            sources.add(source("趋势分析", "/trends"));
            followUps = List.of("增长最快议题有哪些风险？", "生成一份本周舆情简报");
        } else if (question.contains("宿舍")) {
            answer = answerDormIssues();
            type = "analysis";
            sources.add(source("帖子监测", "/monitoring"));
            followUps = List.of("宿舍问题中哪些风险最高？", "给出后勤处置建议");
        } else if (question.contains("诈骗") || question.contains("骗")) {
            answer = answerFraud();
            type = "analysis";
            sources.add(source("网络与数据安全相关帖子", "/monitoring?category=网络与数据安全"));
            followUps = List.of("当前有哪些高风险诈骗事件？", "给出防诈骗宣传建议");
        } else if (question.contains("高风险") || question.contains("风险")) {
            answer = answerHighRisk();
            type = "analysis";
            sources.add(source("事件管理", "/events"));
            followUps = List.of("这些事件的风险判断依据是什么？", "生成处置优先级建议");
        } else if (question.contains("情绪") || question.contains("情感")) {
            answer = answerEmotion();
            type = "analysis";
            sources.add(source("趋势分析", "/trends"));
            followUps = List.of("哪些议题的负面情绪最多？", "负面情绪是否正在上升？");
        } else if (question.contains("值得关注") || question.contains("安全问题") || question.contains("最近")) {
            answer = answerRecentIssues();
            type = "summary";
            sources.add(source("事件管理", "/events"));
            sources.add(source("帖子监测", "/monitoring"));
            followUps = List.of("当前有哪些高风险事件？", "哪些议题增长最快？");
        } else {
            answer = answerDefault();
            type = "default";
            followUps = List.of("最近有哪些值得关注的安全问题？", "生成本周舆情简报");
        }

        boolean llmEnhanced = false;
        if (llmClient.isEnabled()) {
            Optional<String> enhanced = llmClient.answer(
                rawQuestion, history, buildDataContext(), answer);
            if (enhanced.isPresent()) {
                answer = enhanced.get();
                llmEnhanced = true;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("type", type);
        result.put("sources", sources);
        result.put("followUps", followUps);
        result.put("engine", llmEnhanced ? "llm" : "structured");
        result.put("model", llmEnhanced ? llmClient.modelName() : "本地数据分析引擎");
        result.put("dataAsOf", latestDataTime());
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "online", true,
            "llmEnabled", llmClient.isEnabled(),
            "engine", llmClient.isEnabled() ? "hybrid" : "structured",
            "model", llmClient.modelName(),
            "dataAsOf", latestDataTime()
        );
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
        LocalDateTime anchor = posts.stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(LocalDateTime.now());
        LocalDateTime recentStart = anchor.minusDays(7);
        LocalDateTime previousStart = anchor.minusDays(14);
        Map<String, Long> recent = posts.stream()
            .filter(p -> p.getPublishTime() != null && !p.getPublishTime().isBefore(recentStart))
            .filter(p -> effectiveCategory(p) != null)
            .collect(Collectors.groupingBy(this::effectiveCategory, Collectors.counting()));
        Map<String, Long> previous = posts.stream()
            .filter(p -> p.getPublishTime() != null
                && !p.getPublishTime().isBefore(previousStart)
                && p.getPublishTime().isBefore(recentStart))
            .filter(p -> effectiveCategory(p) != null)
            .collect(Collectors.groupingBy(this::effectiveCategory, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("按数据最新时间向前对比两个 7 天周期，议题变化如下：\n\n");

        List<String> sorted = recent.keySet().stream()
            .sorted(Comparator
                .<String>comparingLong(category -> recent.getOrDefault(category, 0L)
                    - previous.getOrDefault(category, 0L))
                .reversed()
                .thenComparing(Comparator.comparingLong(
                    (String category) -> recent.getOrDefault(category, 0L)).reversed()))
            .limit(5)
            .toList();

        int idx = 1;
        for (String category : sorted) {
            long current = recent.getOrDefault(category, 0L);
            long before = previous.getOrDefault(category, 0L);
            long delta = current - before;
            sb.append(idx++).append(". **").append(category).append("**：")
                .append(current).append(" 条，较前一周期")
                .append(delta >= 0 ? "增加 " : "减少 ").append(Math.abs(delta)).append(" 条\n");
        }

        if (!sorted.isEmpty()) {
            String top = sorted.get(0);
            long delta = recent.getOrDefault(top, 0L) - previous.getOrDefault(top, 0L);
            sb.append("\n**增长最快的议题**：").append(top)
                .append("，净增 ").append(delta).append(" 条。");
        } else {
            sb.append("当前两个统计周期内没有足够的分类数据。");
        }
        return sb.toString();
    }

    /**
     * 宿舍相关问题
     */
    private String answerDormIssues() {
        List<Post> posts = postRepository.findAll().stream()
            .filter(p -> "建筑与设施安全".equals(p.getSafetyCategory()))
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
            .filter(p -> "网络与数据安全".equals(p.getSafetyCategory()))
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("根据分析，共识别到 ").append(posts.size()).append(" 条网络与数据安全相关讨论。\n\n");

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
        return "这个问题暂时无法仅靠当前结构化数据准确回答。你可以换一种更具体的问法，例如：\n\n" +
               "- 最近一周有哪些值得关注的校园安全问题？\n" +
               "- 哪些校园安全议题增长最快？\n" +
               "- 最近宿舍相关问题主要集中在哪些方面？\n" +
               "- 当前有哪些高风险事件？\n" +
               "- 生成一份本周校园安全舆情简报\n" +
               "- 诈骗相关问题有哪些？\n\n" +
               "配置大模型 API 后，我还可以在平台数据范围内理解更灵活的问法和连续追问。";
    }

    private List<Map<String, String>> parseHistory(Object raw) {
        if (!(raw instanceof Collection<?> collection)) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> value)) continue;
            String role = Objects.toString(value.get("role"), "");
            String content = Objects.toString(value.get("content"), "").trim();
            if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                result.add(Map.of("role", role, "content",
                    content.substring(0, Math.min(content.length(), 3000))));
            }
        }
        return result.stream().skip(Math.max(0, result.size() - 8)).toList();
    }

    private Map<String, String> source(String label, String route) {
        return Map.of("label", label, "route", route);
    }

    private String effectiveCategory(Post post) {
        return post.getReviewedCategory() != null && !post.getReviewedCategory().isBlank()
            ? post.getReviewedCategory() : post.getSafetyCategory();
    }

    private String latestDataTime() {
        return postRepository.findAll().stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .map(value -> value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
            .orElse("暂无数据");
    }

    /**
     * Only aggregate and event-level fields are sent to the optional model.
     * Raw post authors and other personal identifiers are intentionally excluded.
     */
    private String buildDataContext() {
        List<Post> posts = postRepository.findAll();
        List<EventEntity> events = eventRepository.findAllByOrderByRiskScoreDescCreatedAtDesc();
        Map<String, Long> categories = posts.stream()
            .filter(post -> effectiveCategory(post) != null)
            .collect(Collectors.groupingBy(this::effectiveCategory, Collectors.counting()));
        Map<String, Long> emotions = posts.stream()
            .map(post -> post.getReviewedEmotion() != null ? post.getReviewedEmotion() : post.getEmotion())
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()));

        StringBuilder context = new StringBuilder();
        context.append("数据截至：").append(latestDataTime()).append("\n")
            .append("帖子总量：").append(posts.size()).append("\n")
            .append("事件总量：").append(events.size()).append("\n")
            .append("分类统计：").append(categories).append("\n")
            .append("情绪统计：").append(emotions).append("\n")
            .append("高风险事件数：")
            .append(events.stream().filter(event -> "高".equals(event.getRisk())).count())
            .append("\n重点事件：\n");
        for (EventEntity event : events.stream().limit(8).toList()) {
            context.append("- ").append(event.getTitle())
                .append("；类别=").append(event.getCategory())
                .append("；风险=").append(event.getRisk())
                .append("；评分=").append(event.getRiskScore())
                .append("；讨论量=").append(event.getPostCount())
                .append("；范围=").append(event.getAffectedRange())
                .append("；摘要=").append(event.getSummary())
                .append("\n");
        }
        return context.toString();
    }
}
