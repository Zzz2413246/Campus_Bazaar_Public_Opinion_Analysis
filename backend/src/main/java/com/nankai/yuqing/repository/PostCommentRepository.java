package com.nankai.yuqing.repository;

import com.nankai.yuqing.model.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostCommentRepository extends JpaRepository<PostComment, String> {

    List<PostComment> findByThreadIdOrderByPublishTimeAsc(String threadId);

    Page<PostComment> findByThreadIdOrderByPublishTimeAsc(String threadId, Pageable pageable);

    @Query("SELECT c.id FROM PostComment c")
    List<String> findAllIds();

    @Query("SELECT COUNT(DISTINCT c.threadId) FROM PostComment c")
    long countDistinctThreads();
}
