package com.nankai.yuqing.repository;

import com.nankai.yuqing.model.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventRepository extends JpaRepository<EventEntity, String> {
    List<EventEntity> findAllByOrderByRiskScoreDescCreatedAtDesc();
}
