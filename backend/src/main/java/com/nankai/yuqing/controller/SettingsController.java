package com.nankai.yuqing.controller;

import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisSettingsService;
import com.nankai.yuqing.service.DataImportService;
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
    private final DataImportService dataImportService;

    public SettingsController(PostRepository postRepository,
                              EventRepository eventRepository,
                              AnalysisSettingsService settingsService,
                              DataImportService dataImportService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
        this.dataImportService = dataImportService;
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
            // 阈值和自定义分类规则保存后立即重算，保证刷新各页面时使用的是新设置。
            dataImportService.reanalyzeAll();
            result.put("success", true);
            result.put("message", "设置已保存，分析结果已按新规则刷新");
            result.put("reanalyzed", true);
            return result;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
