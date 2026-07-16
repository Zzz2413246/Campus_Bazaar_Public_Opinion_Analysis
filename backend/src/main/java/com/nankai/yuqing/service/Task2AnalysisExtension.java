package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 任务二占位实现：标准未到位前不猜测评分逻辑，但数据和调用契约已经固定。 */
@Service
public class Task2AnalysisExtension implements AnalysisTaskExtension {

    @Override
    public String code() {
        return "task2";
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code());
        result.put("name", "分析标准任务二");
        result.put("state", "WAITING_FOR_STANDARD");
        result.put("ready", false);
        result.put("analysisInput", List.of(
            "id", "title", "content", "publishTime", "categoryName",
            "commentCount", "likeCount", "viewCount", "safetyCategory",
            "emotion", "riskScore", "location", "problem", "demand", "topic"
        ));
        result.put("standardContract", Map.of(
            "version", "标准版本号",
            "dimensions", "分析维度数组",
            "rules", "各维度判定或评分规则",
            "thresholds", "可选的等级阈值"
        ));
        result.put("message", "任务二标准尚未提供，当前不会影响核心分析结果");
        return result;
    }

    @Override
    public Map<String, Object> execute(List<Post> posts, Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>(status());
        result.put("acceptedPosts", posts.size());
        result.put("receivedStandard", request != null && request.get("standard") != null);
        result.put("executed", false);
        return result;
    }
}
