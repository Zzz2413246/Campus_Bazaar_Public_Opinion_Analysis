package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.EventAction;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventActionRepository;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 事件管理接口
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;
    private final EventActionRepository eventActionRepository;
    private final PostRepository postRepository;
    private final AnalysisService analysisService;

    public EventController(EventRepository eventRepository,
                           EventActionRepository eventActionRepository,
                           PostRepository postRepository,
                           AnalysisService analysisService) {
        this.eventRepository = eventRepository;
        this.eventActionRepository = eventActionRepository;
        this.postRepository = postRepository;
        this.analysisService = analysisService;
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
            m.put("status", normalizeExistingStatus(e.getStatus()));
            m.put("assignee", e.getAssignee());
            m.put("dueAt", e.getDueAt());
            m.put("overdue", isOverdue(e));
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
        result.put("status", normalizeExistingStatus(event.getStatus()));
        result.put("summary", event.getSummary());
        result.put("assignee", event.getAssignee());
        result.put("dueAt", event.getDueAt());
        result.put("resolution", event.getResolution());
        result.put("overdue", isOverdue(event));
        result.put("updatedAt", event.getUpdatedAt());

        // 核心指标
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("postCount", event.getPostCount());
        stats.put("affectedRange", event.getAffectedRange());
        stats.put("urgency", event.getUrgency());
        stats.put("emotion", event.getEmotionSummary());
        stats.put("analyzedComments", relatedPosts.stream()
            .mapToInt(p -> p.getAnalyzedCommentCount() == null ? 0 : p.getAnalyzedCommentCount()).sum());
        result.put("stats", stats);

        // 风险判断依据
        result.put("riskReasons", analysisService.getRiskReasons(event, relatedPosts));
        List<Map<String, Object>> alertTriggers =
            analysisService.getAlertTriggers(event, relatedPosts);
        result.put("alertTriggers", alertTriggers);
        result.put("alertTriggered", !alertTriggers.isEmpty());

        // 相关帖子
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Post p : relatedPosts) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("time", p.getPublishTime() != null ? p.getPublishTime().format(fmt) : "");
            pm.put("source", p.getCategoryName());
            pm.put("emotion", emotionEmoji(p.getEmotion()));
            pm.put("comments", p.getCommentCount());
            pm.put("analyzedComments", p.getAnalyzedCommentCount());
            pm.put("commentRiskAdjustment", p.getCommentRiskAdjustment());
            pm.put("commentSignal", p.getCommentSignal());
            pm.put("analysisBasis", p.getAnalysisBasis());
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

        // 处置记录
        List<Map<String, Object>> actions = new ArrayList<>();
        for (EventAction action : eventActionRepository.findByEventIdOrderByCreatedAtDesc(id)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", action.getId());
            item.put("operator", action.getOperatorName());
            item.put("time", action.getCreatedAt());
            item.put("action", action.getActionName());
            item.put("desc", action.getDescription());
            actions.add(item);
        }
        if (actions.isEmpty()) {
            Map<String, Object> created = new LinkedHashMap<>();
            created.put("operator", "系统");
            created.put("time", event.getCreatedAt());
            created.put("action", "事件已创建");
            created.put("desc", "系统自动聚合识别");
            actions.add(created);
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
        String assignee = body.get("assignee");
        String dueAt = body.get("dueAt");
        String remark = body.get("remark");
        String operator = body.getOrDefault("operator", "管理员");
        String expectedUpdatedAt = body.get("expectedUpdatedAt");

        if (expectedUpdatedAt != null && !expectedUpdatedAt.isBlank() && event.getUpdatedAt() != null) {
            try {
                LocalDateTime expected = LocalDateTime.parse(expectedUpdatedAt);
                if (!event.getUpdatedAt().equals(expected)) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "事件已被其他用户更新，请刷新后重试"
                    );
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "事件版本时间格式无效", e);
            }
        }

        Set<String> allowedStatuses = Set.of("待核实", "处理中", "持续观察", "已解决", "误报");
        if (status != null && !allowedStatuses.contains(status)) {
            return Map.of("error", "无效的处置状态");
        }
        if (status != null) event.setStatus(status);
        if (risk != null) {
            event.setRisk(risk);
        }
        if (assignee != null) event.setAssignee(assignee.trim());
        if (dueAt != null) {
            event.setDueAt(dueAt.isBlank() ? null : LocalDateTime.parse(dueAt));
        }
        if (remark != null && !remark.isBlank()) event.setResolution(remark.trim());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);

        EventAction action = new EventAction();
        action.setEventId(id);
        action.setOperatorName(operator);
        action.setActionName(status == null ? "更新事件信息" : "状态更新为" + status);
        StringBuilder description = new StringBuilder();
        if (risk != null) description.append("风险等级：").append(risk);
        if (assignee != null && !assignee.isBlank()) {
            if (!description.isEmpty()) description.append("；");
            description.append("负责人：").append(assignee.trim());
        }
        if (remark != null && !remark.isBlank()) {
            if (!description.isEmpty()) description.append("；");
            description.append(remark.trim());
        }
        action.setDescription(description.isEmpty() ? "处置信息已更新" : description.toString());
        action.setCreatedAt(LocalDateTime.now());
        eventActionRepository.save(action);

        return Map.of("success", true, "updatedAt", event.getUpdatedAt());
    }

    private boolean isOverdue(EventEntity event) {
        return event.getDueAt() != null
            && event.getDueAt().isBefore(LocalDateTime.now())
            && !Set.of("已解决", "误报").contains(normalizeExistingStatus(event.getStatus()));
    }

    private String normalizeExistingStatus(String status) {
        if (status == null || status.isBlank() || "未处理".equals(status) || "待研判".equals(status)) {
            return "待核实";
        }
        if ("已确认".equals(status)) return "持续观察";
        if ("已忽略".equals(status)) return "误报";
        return status;
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
