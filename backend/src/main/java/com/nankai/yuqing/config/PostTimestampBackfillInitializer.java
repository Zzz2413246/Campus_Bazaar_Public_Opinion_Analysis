package com.nankai.yuqing.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部最终分类文件未携带原帖发布时间时，使用同帖最早一条关联评论的真实时间作为
 * 趋势展示时间。仅补齐空值，不覆盖原始帖子时间，也不把无时间依据的帖子编造成日期。
 */
@Component
@Order(4)
public class PostTimestampBackfillInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PostTimestampBackfillInitializer.class);
    private final ObjectMapper objectMapper;
    private final PostRepository postRepository;

    @Value("${yuqing.comment-file:../comments.json}")
    private String commentFile;

    @Value("${yuqing.classified-results.enabled:false}")
    private boolean classifiedResultsEnabled;

    public PostTimestampBackfillInitializer(ObjectMapper objectMapper, PostRepository postRepository) {
        this.objectMapper = objectMapper;
        this.postRepository = postRepository;
    }

    @Override
    public void run(String... args) {
        if (!classifiedResultsEnabled) return;
        File file = new File(commentFile);
        if (!file.isFile()) {
            log.info("未找到评论时间文件，跳过趋势时间补齐：{}", file.getPath());
            return;
        }
        try {
            List<Map<String, Object>> comments = objectMapper.readValue(file, new TypeReference<>() {});
            Map<String, LocalDateTime> earliestByThread = new HashMap<>();
            for (Map<String, Object> comment : comments) {
                String threadId = text(comment.get("thread_id"));
                LocalDateTime time = parseTime(comment.get("publish_time"));
                if (!threadId.isBlank() && time != null) {
                    earliestByThread.merge(threadId, time,
                        (left, right) -> left.isBefore(right) ? left : right);
                }
            }
            List<Post> updated = new ArrayList<>();
            for (Post post : postRepository.findAll()) {
                LocalDateTime inferredTime = earliestByThread.get(post.getId());
                if (post.getPublishTime() == null && inferredTime != null) {
                    post.setPublishTime(inferredTime);
                    updated.add(post);
                }
            }
            if (!updated.isEmpty()) postRepository.saveAll(updated);
            log.info("趋势时间补齐完成：使用关联评论时间补齐 {} 条帖子（可用时间线程 {} 个）",
                updated.size(), earliestByThread.size());
        } catch (Exception e) {
            log.warn("趋势时间补齐失败，保留现有帖子时间，不影响核心功能", e);
        }
    }

    private LocalDateTime parseTime(Object value) {
        try {
            String time = text(value);
            return time.isBlank() ? null : OffsetDateTime.parse(time).toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
