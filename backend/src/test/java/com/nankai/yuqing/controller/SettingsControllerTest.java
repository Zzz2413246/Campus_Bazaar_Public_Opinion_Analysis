package com.nankai.yuqing.controller;

import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisSettingsService;
import com.nankai.yuqing.service.ReanalysisJobService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SettingsControllerTest {

    @Test
    void updateStartsBackgroundReanalysisJob() {
        PostRepository postRepository = mock(PostRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        AnalysisSettingsService settingsService = new AnalysisSettingsService(null, null) {
            @Override
            public Map<String, Object> update(Map<String, Object> body) {
                return Map.of("categories", java.util.List.of("治安与人身安全"));
            }
        };
        ReanalysisJobService jobService = new ReanalysisJobService(null) {
            @Override
            public synchronized Map<String, Object> start() {
                return Map.of("id", "job-1", "status", "RUNNING");
            }
        };
        SettingsController controller =
            new SettingsController(postRepository, eventRepository, settingsService, jobService);
        Map<String, Object> request = Map.of("categories", java.util.List.of("治安与人身安全"));

        Map<String, Object> result = controller.updateSettings(request);

        assertTrue((Boolean) result.get("success"));
        assertFalse((Boolean) result.get("reanalyzed"));
        assertEquals("job-1", ((Map<?, ?>) result.get("reanalysisJob")).get("id"));
        jobService.shutdown();
    }
}
