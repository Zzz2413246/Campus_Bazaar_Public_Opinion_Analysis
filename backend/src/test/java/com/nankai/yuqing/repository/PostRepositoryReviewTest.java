package com.nankai.yuqing.repository;

import com.nankai.yuqing.model.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:review-test;DB_CLOSE_DELAY=-1")
class PostRepositoryReviewTest {

    @Autowired
    private PostRepository postRepository;

    @Test
    void filtersPendingReviewsAndUsesReviewedCategory() {
        Post pending = post("pending", "消防与用电安全");
        pending.setReviewStatus(null);

        Post corrected = post("corrected", "消防与用电安全");
        corrected.setReviewStatus("已修正");
        corrected.setReviewedCategory("宿舍设施问题");

        postRepository.save(pending);
        postRepository.save(corrected);
        postRepository.flush();

        Page<Post> pendingResult = postRepository.searchPosts(
            null, null, null, null, "待复核", "latest", PageRequest.of(0, 20));
        Page<Post> correctedCategoryResult = postRepository.searchPosts(
            null, "宿舍设施问题", null, null, null, "latest", PageRequest.of(0, 20));

        assertThat(pendingResult.getContent()).extracting(Post::getId).containsExactly("pending");
        assertThat(correctedCategoryResult.getContent()).extracting(Post::getId).containsExactly("corrected");
    }

    @Test
    void sortsByEffectiveRiskLabelWithManualReviewTakingPriority() {
        Post localHigh = post("local-high", "突发事件");
        localHigh.setRiskLevel("高");

        Post externalMedium = post("external-medium", "突发事件");
        externalMedium.setProvidedRiskLevel("中");

        Post reviewedLow = post("reviewed-low", "突发事件");
        reviewedLow.setProvidedRiskLevel("高");
        reviewedLow.setReviewedRiskLevel("低");

        postRepository.save(localHigh);
        postRepository.save(externalMedium);
        postRepository.save(reviewedLow);
        postRepository.flush();

        Page<Post> result = postRepository.searchPosts(
            null, null, null, null, null, "risk", PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Post::getId)
            .containsExactly("local-high", "external-medium", "reviewed-low");
    }

    @Test
    void sortsByInteractionHeat() {
        Post highHeat = post("high-heat", "突发事件");
        highHeat.setViewCount(100);
        highHeat.setCommentCount(30);
        highHeat.setLikeCount(20);

        Post lowHeat = post("low-heat", "突发事件");
        lowHeat.setViewCount(200);
        lowHeat.setCommentCount(1);
        lowHeat.setLikeCount(1);

        postRepository.save(lowHeat);
        postRepository.save(highHeat);
        postRepository.flush();

        Page<Post> result = postRepository.searchPosts(
            null, null, null, null, null, "heat", PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(Post::getId)
            .containsExactly("high-heat", "low-heat");
    }

    private Post post(String id, String category) {
        Post post = new Post();
        post.setId(id);
        post.setTitle(id);
        post.setContent("测试内容");
        post.setSafetyCategory(category);
        post.setEmotion("中性");
        post.setRiskLevel("低");
        post.setPublishTime(LocalDateTime.now());
        return post;
    }
}
