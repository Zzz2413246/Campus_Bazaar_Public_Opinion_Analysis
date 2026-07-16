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
           "AND (:category IS NULL OR (:category = '其他' AND p.safetyCategory IS NULL) OR p.safetyCategory = :category) " +
           "AND (:emotion IS NULL OR p.emotion = :emotion) " +
           "AND (:source IS NULL OR p.categoryName = :source) " +
           "ORDER BY p.publishTime DESC")
    Page<Post> searchPosts(@Param("keyword") String keyword,
                           @Param("category") String category,
                           @Param("emotion") String emotion,
                           @Param("source") String source,
                           Pageable pageable);

    @Query("SELECT p.id FROM Post p")
    List<String> findAllIds();

    @Query("SELECT COUNT(p) FROM Post p")
    long countAll();

    @Query("SELECT p.safetyCategory, COUNT(p) FROM Post p WHERE p.safetyCategory IS NOT NULL GROUP BY p.safetyCategory")
    List<Object[]> countByCategory();

    @Query("SELECT p FROM Post p WHERE p.publishTime >= :start ORDER BY p.publishTime ASC")
    List<Post> findByPublishTimeAfter(@Param("start") java.time.LocalDateTime start);
}
