package com.nankai.yuqing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 将已有数据库的 screening_label 无损迁移到 safety_relevance。 */
@Component
@Order(0)
public class SafetyRelevanceMigration implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SafetyRelevanceMigration.class);
    private final JdbcTemplate jdbcTemplate;

    public SafetyRelevanceMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Integer legacyColumn = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE UPPER(TABLE_NAME) = 'POSTS' AND UPPER(COLUMN_NAME) = 'SCREENING_LABEL'
            """, Integer.class);
        if (legacyColumn != null && legacyColumn > 0) {
            int migrated = jdbcTemplate.update("""
                UPDATE posts SET safety_relevance = CASE UPPER(TRIM(screening_label))
                    WHEN 'SAFETY' THEN 'related'
                    WHEN 'NON_SAFETY' THEN 'unrelated'
                    WHEN 'UNCERTAIN' THEN 'uncertain'
                    ELSE 'uncertain' END
                WHERE safety_relevance IS NULL AND screening_label IS NOT NULL
                """);
            if (migrated > 0) log.info("已迁移 {} 条历史安全相关性数据", migrated);
        }
        jdbcTemplate.update("""
            UPDATE posts SET safety_relevance = CASE UPPER(TRIM(safety_relevance))
                WHEN 'SAFETY' THEN 'related'
                WHEN 'NON_SAFETY' THEN 'unrelated'
                WHEN 'RELATED' THEN 'related'
                WHEN 'UNRELATED' THEN 'unrelated'
                ELSE 'uncertain' END
            WHERE safety_relevance IS NOT NULL
            """);
    }
}
