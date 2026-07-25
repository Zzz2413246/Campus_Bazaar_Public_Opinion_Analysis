package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostControllerCommentCountTest {

    @Test
    void listUsesActualLinkedCommentCountAndKeepsSourceCountSeparately() {
        PostRepository postRepository = mock(PostRepository.class);
        PostCommentRepository commentRepository = mock(PostCommentRepository.class);
        PostController controller = new PostController(postRepository, commentRepository, null);
        Post post = new Post();
        post.setId("2127696592");
        post.setCommentCount(0);

        when(postRepository.searchPosts(
            isNull(), isNull(), isNull(), isNull(), isNull(), eq("latest"), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(post)));
        when(commentRepository.countByThreadIds(List.of("2127696592")))
            .thenReturn(List.<Object[]>of(new Object[] {"2127696592", 4L}));

        Map<String, Object> response =
            controller.list(null, null, null, null, null, "latest", 1, 20);
        @SuppressWarnings("unchecked")
        Map<String, Object> item = ((List<Map<String, Object>>) response.get("data")).get(0);

        assertEquals(4L, item.get("commentCount"));
        assertEquals(0, item.get("sourceCommentCount"));
    }
}
