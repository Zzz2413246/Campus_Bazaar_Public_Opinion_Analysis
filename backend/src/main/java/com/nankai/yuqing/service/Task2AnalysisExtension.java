package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 任务二末代分类工作流扩展入口。 */
@Service
public class Task2AnalysisExtension implements AnalysisTaskExtension {
    private final Task2RealtimeClassificationService classifier;

    public Task2AnalysisExtension(Task2RealtimeClassificationService classifier) { this.classifier = classifier; }

    @Override
    public String code() {
        return "task2";
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code());
        result.put("name", "任务二实时分类工作流");
        result.put("state", classifier.enabled() ? "READY" : "NEEDS_MODEL_CONFIG");
        result.put("ready", classifier.enabled());
        result.put("workflowVersion", Task2RealtimeClassificationService.VERSION);
        result.put("model", classifier.modelName());
        result.put("stages", List.of("主帖与评论初筛", "18类完整结构化分析", "低中高风险评估"));
        result.put("analysisInput", List.of(
            "id", "title", "content", "publishTime", "categoryName",
            "commentCount", "likeCount", "viewCount", "safetyCategory",
            "emotion", "location", "problem", "demand", "topic"
        ));
        result.put("supportedCategories", SafetyCategoryStandard.CODE_TO_NAME);
        result.put("requestContract", Map.of(
            "postIds", "可选；不传时从全量数据中按limit截取",
            "limit", "1-20，默认5",
            "dryRun", "true时只返回结果，不写入数据库"
        ));
        result.put("message", classifier.enabled()
            ? "末代分类逻辑已接入，可小批量试运行或正式写回；人工复核结果不会被覆盖"
            : "分类逻辑已接入；部署时配置 YUQING_AI_API_KEY、YUQING_AI_BASE_URL 和 YUQING_AI_MODEL 后启用");
        return result;
    }

    @Override
    public Map<String, Object> execute(List<Post> posts, Map<String, Object> request) {
        if (!classifier.enabled()) return Map.of("error", "任务二模型未配置", "status", status());
        int limit = Math.max(1, Math.min(20, number(request.get("limit"), 5)));
        boolean dryRun = Boolean.TRUE.equals(request.get("dryRun"));
        Set<String> requested = stringSet(request.get("postIds"));
        List<Post> selected = posts.stream()
            .filter(p -> requested.isEmpty() || requested.contains(p.getId()))
            .limit(limit).toList();
        Map<String,Object> result = new LinkedHashMap<>(status());
        result.putAll(classifier.classify(selected, dryRun));
        if (!requested.isEmpty()) {
            Set<String> found = new HashSet<>(selected.stream().map(Post::getId).toList());
            result.put("missingPostIds", requested.stream().filter(id -> !found.contains(id)).toList());
        }
        return result;
    }

    private int number(Object value,int fallback){return value instanceof Number n?n.intValue():fallback;}
    private Set<String> stringSet(Object value){if(!(value instanceof Collection<?> c))return Set.of();Set<String>s=new LinkedHashSet<>();for(Object x:c){String v=Objects.toString(x,"").trim();if(!v.isBlank())s.add(v);}return s;}
}
