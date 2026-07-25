package com.nankai.yuqing.controller;

import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisSettingsService;
import com.nankai.yuqing.service.ReanalysisJobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
    private final AnalysisSettingsService settingsService;
    private final ReanalysisJobService reanalysisJobService;

    public SettingsController(PostRepository postRepository,
                              EventRepository eventRepository,
                              AnalysisSettingsService settingsService,
                              ReanalysisJobService reanalysisJobService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
        this.reanalysisJobService = reanalysisJobService;
    }

    @GetMapping
    public Map<String, Object> getSettings() {
        Map<String, Object> result = new LinkedHashMap<>(settingsService.getSettings());

        // 数据源配置
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(source("校园集市", "校内二手交易与讨论平台", true, "正常"));
        sources.add(source("小红书", "社交媒体平台", false, "未接入"));
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
        try {
            Map<String, Object> result = new LinkedHashMap<>(settingsService.update(body));
            // 数据量较大时同步重算会阻塞设置保存，因此统一交给后台任务处理。
            Map<String, Object> job = reanalysisJobService.start();
            result.put("success", true);
            result.put("message", "设置已保存，后台重新分析已启动");
            result.put("reanalyzed", false);
            result.put("reanalysisJob", job);
            return result;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
