package com.nankai.yuqing.repository;

import com.nankai.yuqing.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AuditLog> findByActionCodeOrderByCreatedAtDesc(String actionCode, Pageable pageable);
    Page<AuditLog> findByOperatorNameOrderByCreatedAtDesc(String operatorName, Pageable pageable);
    long countByCreatedAtAfter(LocalDateTime time);
    long countByStatusAndCreatedAtAfter(String status, LocalDateTime time);
}
