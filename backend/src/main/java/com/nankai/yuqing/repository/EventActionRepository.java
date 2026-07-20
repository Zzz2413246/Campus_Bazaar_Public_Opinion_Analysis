package com.nankai.yuqing.repository;

import com.nankai.yuqing.model.EventAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventActionRepository extends JpaRepository<EventAction, Long> {
    List<EventAction> findByEventIdOrderByCreatedAtDesc(String eventId);
}
