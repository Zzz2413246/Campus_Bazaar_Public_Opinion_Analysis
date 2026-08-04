package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.SystemSetting;
import com.nankai.yuqing.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AnalysisSettingsServiceTest {

    @Test
    void settingsSurviveServiceReload() {
        AtomicReference<SystemSetting> stored = new AtomicReference<>();
        SystemSettingRepository repository = repository(stored);
        AnalysisSettingsService service = new AnalysisSettingsService(repository, new ObjectMapper());

        service.update(settingsPayload());

        AnalysisSettingsService reloaded = new AnalysisSettingsService(repository, new ObjectMapper());
        AnalysisSettingsService.Snapshot snapshot = reloaded.getSnapshot();
        assertTrue(snapshot.categories().contains("网络与信息安全"));
        assertEquals(List.of("账号泄露", "钓鱼邮件"), snapshot.categoryRules().get("网络与信息安全"));
    }

    @Test
    void customCategoryParticipatesInAnalysis() {
        AnalysisSettingsService settings = new AnalysisSettingsService(repository(new AtomicReference<>()), new ObjectMapper());
        settings.update(settingsPayload());
        AnalysisService analysis = new AnalysisService(null, null, settings);
        Post post = post("校园账号泄露提醒", "收到钓鱼邮件后账号泄露，请大家注意");

        analysis.analyzePost(post);

        assertEquals("网络与信息安全", post.getSafetyCategory());
        assertEquals("网络与信息安全相关讨论", post.getTopic());
        assertTrue(post.getClassificationConfidence() >= 60);
    }

    @Test
    void legacyRiskThresholdPayloadIsIgnored() {
        AnalysisSettingsService settings = new AnalysisSettingsService(repository(new AtomicReference<>()), new ObjectMapper());
        Map<String, Object> payload = settingsPayload();
        payload.put("riskThresholds", Map.of("high", 50, "medium", 50));
        Map<String, Object> result = settings.update(payload);
        assertFalse(result.containsKey("riskThresholds"));
    }

    @Test
    void settingsResponseDoesNotExposeNumericThresholds() {
        AnalysisSettingsService service = new AnalysisSettingsService(repository(new AtomicReference<>()), new ObjectMapper());
        assertFalse(service.getSettings().containsKey("riskThresholds"));
    }

    @Test
    void alertRulesAreValidatedAndSurviveReload() {
        AtomicReference<SystemSetting> stored = new AtomicReference<>();
        AnalysisSettingsService service =
            new AnalysisSettingsService(repository(stored), new ObjectMapper());
        Map<String, Object> payload = settingsPayload();
        payload.put("alertRules", Map.of(
            "negativeRatioPercent", 120,
            "burstWindowHours", 6,
            "urgentKeywords", List.of("线下行动", "聚集")
        ));
        service.update(payload);

        AnalysisSettingsService reloaded =
            new AnalysisSettingsService(repository(stored), new ObjectMapper());
        AnalysisSettingsService.AlertRules rules = reloaded.getSnapshot().alertRules();

        assertEquals(100, rules.negativeRatioPercent());
        assertEquals(6, rules.burstWindowHours());
        assertEquals(List.of("线下行动", "聚集"), rules.urgentKeywords());
    }

    private Map<String, Object> settingsPayload() {
        Map<String, Object> body = new LinkedHashMap<>();
        List<String> categories = new ArrayList<>(SafetyCategoryStandard.CATEGORIES);
        categories.add("网络与信息安全");
        body.put("categories", categories);
        body.put("categoryRules", Map.of("网络与信息安全", List.of("账号泄露", "钓鱼邮件")));
        return body;
    }

    private SystemSettingRepository repository(AtomicReference<SystemSetting> stored) {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> {
            SystemSetting value = invocation.getArgument(0);
            stored.set(value);
            return value;
        });
        return repository;
    }

    private Post post(String title, String content) {
        Post post = new Post();
        post.setId(title);
        post.setTitle(title);
        post.setContent(content);
        post.setCategoryName("打听求助");
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setViewCount(10);
        return post;
    }
}
