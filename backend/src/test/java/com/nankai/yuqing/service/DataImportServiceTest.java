package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DataImportServiceTest {

    @Test
    void importedAiRiskLabelIsStoredWithoutRequiringNumericScore() {
        PostRepository postRepository = mock(PostRepository.class);
        EventRepository eventRepository = mock(EventRepository.class);
        TrackingAnalysisService analysisService = new TrackingAnalysisService();
        when(postRepository.findAll()).thenReturn(List.of());
        when(postRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        DataImportService service = new DataImportService(
            postRepository, eventRepository, analysisService, null);
        Map<String, Object> result = service.importPosts(List.of(Map.of(
            "id", "external-ai-1",
            "title", "AI标签测试",
            "content", "只提供风险标签，不提供风险分数",
            "risk_level", "高风险"
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).saveAll(captor.capture());
        assertEquals("高", captor.getValue().get(0).getProvidedRiskLevel());
        assertEquals(1, result.get("imported"));
        assertEquals(true, analysisService.analyzed);
        assertEquals(true, analysisService.aggregated);
    }

    @Test
    void existingPostCanReceiveAiRiskLabelWithoutDuplicateImport() {
        Post existing = new Post();
        existing.setId("existing-1");
        PostRepository postRepository = mock(PostRepository.class);
        when(postRepository.findAll()).thenReturn(List.of(existing));
        when(postRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        TrackingAnalysisService analysisService = new TrackingAnalysisService();
        DataImportService service = new DataImportService(
            postRepository,
            mock(EventRepository.class),
            analysisService,
            null);

        Map<String, Object> result = service.importPosts(List.of(Map.of(
            "id", "existing-1",
            "riskLevel", "中"
        )));

        assertEquals("中", existing.getProvidedRiskLevel());
        assertEquals(1, result.get("updatedRiskLabels"));
        assertEquals(false, analysisService.analyzed);
        assertEquals(true, analysisService.aggregated);
    }

    @Test
    void duplicateIdsInOneBatchAreMergedIntoOneNewPost() {
        PostRepository postRepository = mock(PostRepository.class);
        when(postRepository.findAll()).thenReturn(List.of());
        when(postRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        TrackingAnalysisService analysisService = new TrackingAnalysisService();
        DataImportService service = new DataImportService(
            postRepository, mock(EventRepository.class), analysisService, null);

        Map<String, Object> result = service.importPosts(List.of(
            Map.of("id", "same-1", "title", "初始标题", "view_count", 1),
            Map.of("id", "same-1", "title", "更新标题", "view_count", 9)
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("更新标题", captor.getValue().get(0).getTitle());
        assertEquals(9, captor.getValue().get(0).getViewCount());
        assertEquals(1, result.get("imported"));
        assertEquals(1, result.get("duplicatesMerged"));
    }

    @Test
    void sparseUpdateRefreshesRawFieldsWithoutErasingReviewOrExistingContent() {
        Post existing = new Post();
        existing.setId("existing-2");
        existing.setContent("保留正文");
        existing.setViewCount(10);
        existing.setReviewedCategory("人工分类");
        existing.setReviewNote("人工结论");
        PostRepository postRepository = mock(PostRepository.class);
        when(postRepository.findAll()).thenReturn(List.of(existing));
        when(postRepository.saveAll(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));
        TrackingAnalysisService analysisService = new TrackingAnalysisService();
        DataImportService service = new DataImportService(
            postRepository, mock(EventRepository.class), analysisService, null);

        Map<String, Object> result =
            service.importPosts(List.of(Map.of("id", "existing-2", "view_count", 88)));

        assertEquals(88, existing.getViewCount());
        assertEquals("保留正文", existing.getContent());
        assertEquals("人工分类", existing.getReviewedCategory());
        assertEquals("人工结论", existing.getReviewNote());
        assertEquals(1, result.get("updated"));
        assertEquals(true, analysisService.analyzed);
    }

    @Test
    void invalidPublishTimeDoesNotBecomeCurrentTime() {
        PostRepository postRepository = mock(PostRepository.class);
        when(postRepository.findAll()).thenReturn(List.of());
        when(postRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        DataImportService service = new DataImportService(
            postRepository, mock(EventRepository.class), new TrackingAnalysisService(), null);

        service.importPosts(List.of(Map.of(
            "id", "bad-time",
            "title", "时间异常",
            "publish_time", "not-a-time"
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Post>> captor = ArgumentCaptor.forClass(List.class);
        verify(postRepository).saveAll(captor.capture());
        assertNull(captor.getValue().get(0).getPublishTime());
    }

    private static class TrackingAnalysisService extends AnalysisService {
        boolean analyzed;
        boolean aggregated;

        TrackingAnalysisService() {
            super(null, null);
        }

        @Override
        public void analyzeAllPosts() {
            analyzed = true;
        }

        @Override
        public void aggregateEvents() {
            aggregated = true;
        }
    }
}
