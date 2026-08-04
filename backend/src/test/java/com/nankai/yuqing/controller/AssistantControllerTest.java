package com.nankai.yuqing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AssistantLlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    private AssistantController controller;

    @BeforeEach
    void setUp() {
        PostRepository posts = mock(PostRepository.class);
        EventRepository events = mock(EventRepository.class);
        AssistantLlmClient llm = new AssistantLlmClient(
            RestClient.builder(), new ObjectMapper(), "", "", "", 30, false, 600);
        when(posts.findAll()).thenReturn(List.of());
        when(events.findAll()).thenReturn(List.of());
        when(events.findAllByOrderByRiskScoreDescCreatedAtDesc()).thenReturn(List.of());
        controller = new AssistantController(posts, events, llm);
    }

    @Test
    void specificHighRiskIntentWinsOverBroadRecentIntent() {
        Map<String, Object> result = controller.query(Map.of(
            "question", "最近有哪些高风险事件？",
            "history", List.of()
        ));

        assertEquals("analysis", result.get("type"));
        assertTrue(result.get("answer").toString().contains("没有高风险"));
        assertEquals("structured", result.get("engine"));
    }

    @Test
    void briefingProvidesFollowUpsAndTraceableSource() {
        Map<String, Object> result = controller.query(Map.of(
            "question", "生成本周舆情简报"
        ));

        assertEquals("report", result.get("type"));
        assertTrue(result.get("followUps") instanceof List<?>);
        assertTrue(result.get("sources") instanceof List<?>);
    }
}
