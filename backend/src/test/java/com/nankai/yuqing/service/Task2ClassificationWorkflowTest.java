package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Task2ClassificationWorkflowTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void appliesHighRiskFloorForFireSmokeAndKeepsCommentIdentity() throws Exception {
        FakeClient client = new FakeClient(
            json("{\"post_id\":\"p1\",\"safety_relevance\":\"related\",\"confidence\":0.91}"),
            json("{\"post_id\":\"p1\",\"post_analysis\":{\"safety_relevance\":\"related\",\"is_event_candidate\":true,\"safety_category\":\"fire_electrical\",\"reason\":\"插座冒烟\",\"evidence_spans\":[\"插座突然冒烟\"],\"confidence\":0.93},\"comment_analyses\":[{\"comment_id\":\"c1\",\"relevance\":\"RELEVANT\",\"is_safety_related\":true,\"viewpoint\":\"需断电\",\"sentiment\":\"NEGATIVE\"}],\"discussion_analysis\":{\"safety_relevance\":\"related\",\"viewpoint_clusters\":[{\"viewpoint\":\"立即处置\",\"comment_ids\":[\"c1\"]}],\"controversies\":[],\"safety_clues\":[\"冒烟\"],\"discussion_summary\":\"需要立即排查\"}}"),
            json("{\"risk_level\":\"LOW\"}")
        );
        Task2ClassificationWorkflow workflow = new Task2ClassificationWorkflow(client, prompts());
        Post post = post("p1", "宿舍插座突然冒烟，墙内还有焦味");
        PostComment comment = comment("c1", "p1", "先断电并联系宿管");

        Task2ClassificationWorkflow.Result result = workflow.process(post, List.of(comment));

        assertEquals("fire_electrical", result.categoryCode());
        assertEquals("HIGH", result.riskLevel());
        assertEquals("POST_AND_COMMENTS", result.riskSource());
        assertEquals("负面", result.comments().get("c1").emotion());
    }

    @Test
    void rejectsCommentIdsNotPresentInSource() throws Exception {
        FakeClient client = new FakeClient(
            json("{\"post_id\":\"p2\",\"safety_relevance\":\"related\",\"confidence\":0.9}"),
            json("{\"post_id\":\"p2\",\"post_analysis\":{\"safety_relevance\":\"related\",\"is_event_candidate\":true,\"safety_category\":\"traffic\",\"reason\":\"车辆逆行\",\"evidence_spans\":[\"车辆逆行\"],\"confidence\":0.9},\"comment_analyses\":[{\"comment_id\":\"wrong\",\"relevance\":\"RELEVANT\",\"is_safety_related\":true,\"viewpoint\":\"危险\",\"sentiment\":\"NEGATIVE\"}],\"discussion_analysis\":{\"safety_relevance\":\"related\",\"viewpoint_clusters\":[],\"controversies\":[],\"safety_clues\":[],\"discussion_summary\":\"\"}}")
        );
        Task2ClassificationWorkflow workflow = new Task2ClassificationWorkflow(client, prompts());
        assertThrows(IllegalArgumentException.class,
            () -> workflow.process(post("p2", "校门口车辆逆行"), List.of(comment("c2", "p2", "很危险"))));
    }

    @Test
    void skipsClearlyNonSafetyWithoutFullAnalysis() throws Exception {
        FakeClient client = new FakeClient(json("{\"post_id\":\"p3\",\"safety_relevance\":\"unrelated\",\"confidence\":0.98}"));
        Task2ClassificationWorkflow.Result result = new Task2ClassificationWorkflow(client, prompts())
            .process(post("p3", "食堂二楼哪家面好吃"), List.of());
        assertEquals("SKIPPED_UNRELATED", result.status());
        assertEquals("not_safety", result.categoryCode());
        assertEquals(0, client.responses.size());
    }

    private static Task2PromptCatalog prompts() { return new Task2PromptCatalog(); }
    private static JsonNode json(String value) throws Exception { return MAPPER.readTree(value); }
    private static Post post(String id, String content) { Post p=new Post();p.setId(id);p.setTitle(content);p.setContent(content);p.setPublishTime(LocalDateTime.now());return p; }
    private static PostComment comment(String id,String thread,String content){PostComment c=new PostComment();c.setId(id);c.setThreadId(thread);c.setContent(content);c.setPublishTime(LocalDateTime.now());return c;}

    private static class FakeClient extends Task2StructuredLlmClient {
        private final ArrayDeque<JsonNode> responses = new ArrayDeque<>();
        FakeClient(JsonNode... responses) { super(null, MAPPER, "key", "http://local", "model", 30, 1000); this.responses.addAll(List.of(responses)); }
        @Override public JsonNode generate(String systemPrompt, Object input) { return responses.removeFirst(); }
    }
}
