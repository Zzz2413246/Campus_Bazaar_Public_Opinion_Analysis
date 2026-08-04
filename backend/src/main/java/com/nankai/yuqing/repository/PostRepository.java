package com.nankai.yuqing.repository;

import com.nankai.yuqing.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, String> {

    List<Post> findBySafetyCategory(String safetyCategory);

    List<Post> findByEventId(String eventId);

    @Query("SELECT p FROM Post p WHERE " +
           "(:keyword IS NULL OR p.title LIKE %:keyword% OR p.content LIKE %:keyword%) " +
           "AND (:category IS NULL " +
           "  OR (:category = '非安全内容' AND p.screeningLabel = 'NON_SAFETY') " +
           "  OR (:category <> '非安全内容' AND COALESCE(p.reviewedCategory, p.safetyCategory) = :category)) " +
           "AND (:emotion IS NULL OR COALESCE(p.reviewedEmotion, p.emotion) = :emotion) " +
           "AND (:source IS NULL OR p.categoryName = :source) " +
           "AND (:reviewStatus IS NULL " +
           "  OR (:reviewStatus = '待复核' AND (p.reviewStatus IS NULL OR p.reviewStatus = '待复核')) " +
           "  OR p.reviewStatus = :reviewStatus) " +
           "ORDER BY " +
           "CASE WHEN :sortBy = 'risk' THEN " +
           "  CASE COALESCE(p.reviewedRiskLevel, p.providedRiskLevel, p.riskLevel) " +
           "    WHEN '高' THEN 3 WHEN '中' THEN 2 ELSE 1 END " +
           "END DESC, " +
           "CASE WHEN :sortBy = 'heat' THEN " +
           "  COALESCE(p.viewCount, 0) + COALESCE(p.commentCount, 0) * 20 + COALESCE(p.likeCount, 0) * 5 " +
           "END DESC, " +
           "p.publishTime DESC")
    Page<Post> searchPosts(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("emotion") String emotion,
                           @Param("source") String source,
                           @Param("reviewStatus") String reviewStatus,
                           @Param("sortBy") String sortBy,
                           Pageable pageable);

    @Query("SELECT p.id FROM Post p")
    List<String> findAllIds();

    @Query("SELECT COUNT(p) FROM Post p")
    long countAll();

    @Query("SELECT COALESCE(p.reviewedCategory, p.safetyCategory), COUNT(p) FROM Post p " +
           "WHERE COALESCE(p.reviewedCategory, p.safetyCategory) IS NOT NULL " +
           "GROUP BY COALESCE(p.reviewedCategory, p.safetyCategory)")
    List<Object[]> countByCategory();

    @Query("SELECT p FROM Post p WHERE p.publishTime >= :start ORDER BY p.publishTime ASC")
    List<Post> findByPublishTimeAfter(@Param("start") java.time.LocalDateTime start);
}
