package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ClassifiedResultImportServiceTest {

    @Test
    void importsAllLabelsAndUsesFinalCategoryNames() throws Exception {
        PostRepository posts = mock(PostRepository.class);
        PostCommentRepository comments = mock(PostCommentRepository.class);
        EventRepository events = mock(EventRepository.class);
        boolean[] aggregated = {false};
        AnalysisService analysis = new AnalysisService(null, null) {
            @Override
            public void aggregateEvents() {
                aggregated[0] = true;
            }
        };
        when(posts.findAll()).thenReturn(List.of());
        when(comments.findAll()).thenReturn(List.of());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode safety = mapper.readTree("""
            {"post_id":"1","publish_time":"2026-07-15T18:12:36+08:00",
             "processing_status":"ANALYZED","overall_screening_label":"SAFETY",
             "post_screening":{"confidence":0.98},"full_analysis":{"post_analysis":{
             "safety_category":"personal_security","reason":"存在持续跟踪威胁","evidence_spans":["有人持续尾随"]},
             "comment_analyses":[],"discussion_analysis":{"discussion_summary":"需要关注"}}}
            """);
        JsonNode uncertain = mapper.readTree("""
            {"post_id":"2","processing_status":"NEEDS_VERIFICATION","overall_screening_label":"UNCERTAIN",
             "post_screening":{"confidence":0.55},"full_analysis":{"post_analysis":{
             "safety_category":"undetermined","reason":"证据不足"},"comment_analyses":[],"discussion_analysis":{}}}
            """);
        JsonNode nonSafety = mapper.readTree("""
            {"post_id":"3","processing_status":"SKIPPED_NON_SAFETY","overall_screening_label":"NON_SAFETY"}
            """);

        ClassifiedResultImportService service = new ClassifiedResultImportService(
            posts, comments, events, analysis);
        Map<String, Object> result = service.synchronize(List.of(safety, uncertain, nonSafety));

        ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
        verify(posts).saveAll(captor.capture());
        assertTrue(aggregated[0]);
        List<Post> saved = captor.getValue();
        assertEquals(3, saved.size());
        assertTrue(saved.stream().anyMatch(p -> "个人安全".equals(p.getSafetyCategory())));
        assertTrue(saved.stream().anyMatch(p -> "疑似主题无法确定".equals(p.getSafetyCategory())));
        assertTrue(saved.stream().anyMatch(p -> "unrelated".equals(p.getSafetyRelevance()) && p.getSafetyCategory() == null));
        assertTrue(saved.stream().anyMatch(p -> "1".equals(p.getId())
            && LocalDateTime.parse("2026-07-15T18:12:36").equals(p.getPublishTime())));
        assertTrue(saved.stream().anyMatch(p -> "1".equals(p.getId())
            && Long.valueOf(1784110356L).equals(p.getPublishTimestamp())));
        assertEquals(3, result.get("retained"));
        assertEquals(1, result.get("unrelated"));
    }

    @Test
    void keepsRecordsWithoutFinalLabelForManualReview() throws Exception {
        PostRepository posts = mock(PostRepository.class);
        PostCommentRepository comments = mock(PostCommentRepository.class);
        EventRepository events = mock(EventRepository.class);
        AnalysisService analysis = new AnalysisService(null, null) {
            @Override public void aggregateEvents() {}
        };
        when(posts.findAll()).thenReturn(List.of());
        when(comments.findAll()).thenReturn(List.of());

        JsonNode incomplete = new ObjectMapper().readTree("""
            {"post_id":"incomplete-1","processing_status":"FAILED","full_analysis":{}}
            """);

        Map<String, Object> result = new ClassifiedResultImportService(posts, comments, events, analysis)
            .synchronize(List.of(incomplete));

        ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
        verify(posts).saveAll(captor.capture());
        Post saved = captor.getValue().get(0);
        assertEquals("uncertain", saved.getSafetyRelevance());
        assertEquals("待复核", saved.getReviewStatus());
        assertEquals(1, result.get("normalizedUncertain"));
    }

    @Test
    void startupSyncPreservesManualReviewAndExistingEventState() throws Exception {
        PostRepository posts = mock(PostRepository.class);
        PostCommentRepository comments = mock(PostCommentRepository.class);
        EventRepository events = mock(EventRepository.class);
        AnalysisService analysis = new AnalysisService(null, null) {
            @Override public void aggregateEvents() {}
        };

        Post reviewed = new Post();
        reviewed.setId("1");
        reviewed.setReviewStatus("已修正");
        reviewed.setReviewedCategory("财产安全");
        reviewed.setReviewedRiskLevel("高");
        reviewed.setReviewedEmotion("负面");
        reviewed.setReviewNote("人工核实后修正");
        reviewed.setReviewer("值班员");
        reviewed.setReviewedAt(LocalDateTime.parse("2026-08-01T10:00:00"));
        when(posts.findAll()).thenReturn(List.of(reviewed));

        PostComment stale = new PostComment();
        stale.setId("old-comment");
        stale.setThreadId("1");
        when(comments.findAll()).thenReturn(List.of(stale), List.of());

        JsonNode safety = new ObjectMapper().readTree("""
            {"post_id":"1","processing_status":"ANALYZED","overall_screening_label":"SAFETY",
             "full_analysis":{"post_analysis":{"safety_category":"personal_security","reason":"外部结论"},
             "comment_analyses":[],"discussion_analysis":{}}}
            """);

        new ClassifiedResultImportService(posts, comments, events, analysis)
            .synchronize(List.of(safety));

        assertEquals("已修正", reviewed.getReviewStatus());
        assertEquals("财产安全", reviewed.getReviewedCategory());
        assertEquals("值班员", reviewed.getReviewer());
        assertEquals("人工核实后修正", reviewed.getReviewNote());
        verify(comments).deleteAllInBatch(anyList());
        verify(events, never()).deleteAllInBatch();
    }
}
