package com.nankai.yuqing.service;

import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentImportServiceTest {

    @Test
    void duplicateCommentIdsInOneBatchAreMergedAndSavedOnce() {
        PostCommentRepository commentRepository = mock(PostCommentRepository.class);
        PostRepository postRepository = mock(PostRepository.class);
        when(commentRepository.findAll()).thenReturn(List.of());
        when(postRepository.findAllIds()).thenReturn(List.of("post-1"));
        when(commentRepository.saveAll(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));
        CommentImportService service = new CommentImportService(commentRepository, postRepository);

        Map<String, Object> result = service.importComments(List.of(
            Map.of("comment_id", "comment-1", "thread_id", "post-1", "content", "第一版"),
            Map.of("comment_id", "comment-1", "thread_id", "post-1", "content", "最终版")
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<PostComment>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(commentRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("最终版", captor.getValue().iterator().next().getContent());
        assertEquals(1, result.get("imported"));
        assertEquals(0, result.get("updated"));
        assertEquals(1, result.get("duplicatesMerged"));
        assertEquals(0L, result.get("unmatched"));
    }

    @Test
    void commentArrivingBeforeItsPostIsRetainedAsUnmatched() {
        PostCommentRepository commentRepository = mock(PostCommentRepository.class);
        PostRepository postRepository = mock(PostRepository.class);
        when(commentRepository.findAll()).thenReturn(List.of());
        when(postRepository.findAllIds()).thenReturn(List.of());
        when(commentRepository.saveAll(anyCollection())).thenAnswer(invocation -> invocation.getArgument(0));
        CommentImportService service = new CommentImportService(commentRepository, postRepository);

        Map<String, Object> result = service.importComments(List.of(
            Map.of("comment_id", "early-comment", "thread_id", "future-post", "content", "先到评论")
        ));

        verify(commentRepository).saveAll(anyCollection());
        assertEquals(1, result.get("imported"));
        assertEquals(1L, result.get("unmatched"));
    }
}
