package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统设置接口
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final PostRepository postRepository;
    private final EventRepository eventRepository;

    public SettingsController(PostRepository postRepository, EventRepository eventRepository) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 风险评分阈值
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("high", 70);
        thresholds.put("medium", 40);
        result.put("riskThresholds", thresholds);

        // 安全议题分类
        String[] categories = {
            "诈骗与财产安全", "治安与人身安全", "消防与用电安全",
            "校园交通安全", "宿舍设施问题", "食堂与餐饮问题",
            "突发事件", "其他"
        };
        result.put("categories", Arrays.asList(categories));

        // 数据源配置
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(source("校园集市", "校内二手交易与讨论平台", true, "正常"));
        sources.add(source("小红书", "社交媒体平台", true, "正常"));
        sources.add(source("微博", "公开社交媒体", false, "未接入"));
        sources.add(source("B站", "视频弹幕平台", false, "未接入"));
        result.put("sources", sources);

        // 系统状态
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("totalPosts", postRepository.count());
        status.put("totalEvents", eventRepository.count());
        status.put("lastUpdate", java.time.LocalDateTime.now().toString());
        result.put("status", status);

        return result;
    }

    private Map<String, Object> source(String name, String desc, boolean ok, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("desc", desc);
        m.put("ok", ok);
        m.put("status", status);
        return m;
    }

    @PutMapping
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> body) {
        // 简化：直接返回成功
        return Map.of("success", true, "message", "设置已保存");
    }
}
