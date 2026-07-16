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

        service.update(settingsPayload(85, 55));

        AnalysisSettingsService reloaded = new AnalysisSettingsService(repository, new ObjectMapper());
        AnalysisSettingsService.Snapshot snapshot = reloaded.getSnapshot();
        assertEquals(85, snapshot.highThreshold());
        assertEquals(55, snapshot.mediumThreshold());
        assertTrue(snapshot.categories().contains("网络与信息安全"));
        assertEquals(List.of("账号泄露", "钓鱼邮件"), snapshot.categoryRules().get("网络与信息安全"));
    }

    @Test
    void customCategoryParticipatesInAnalysis() {
        AnalysisSettingsService settings = new AnalysisSettingsService(repository(new AtomicReference<>()), new ObjectMapper());
        settings.update(settingsPayload(85, 55));
        AnalysisService analysis = new AnalysisService(null, null, settings);
        Post post = post("校园账号泄露提醒", "收到钓鱼邮件后账号泄露，请大家注意");

        analysis.analyzePost(post);

        assertEquals("网络与信息安全", post.getSafetyCategory());
        assertEquals("网络与信息安全相关讨论", post.getTopic());
        assertTrue(post.getClassificationConfidence() >= 60);
    }

    @Test
    void configuredRiskThresholdChangesLevel() {
        AnalysisSettingsService settings = new AnalysisSettingsService(repository(new AtomicReference<>()), new ObjectMapper());
        settings.update(settingsPayload(90, 60));
        AnalysisService analysis = new AnalysisService(null, null, settings);
        Post post = post("大家注意不要被骗", "有人在东门骗钱，已经报警，请大家注意防诈骗");
        post.setViewCount(3000);
        post.setCommentCount(30);

        analysis.analyzePost(post);

        assertTrue(post.getRiskScore() >= 60);
        assertTrue(post.getRiskScore() < 90);
        assertEquals("中", post.getRiskLevel());
    }

    @Test
    void invalidThresholdsAreRejected() {
        AnalysisSettingsService service = new AnalysisSettingsService(repository(new AtomicReference<>()), new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> service.update(settingsPayload(50, 50)));
    }

    private Map<String, Object> settingsPayload(int high, int medium) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("riskThresholds", Map.of("high", high, "medium", medium));
        body.put("categories", List.of(
            "诈骗与财产安全", "治安与人身安全", "消防与用电安全", "校园交通安全",
            "宿舍设施问题", "食堂与餐饮问题", "突发事件", "其他", "网络与信息安全"
        ));
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
