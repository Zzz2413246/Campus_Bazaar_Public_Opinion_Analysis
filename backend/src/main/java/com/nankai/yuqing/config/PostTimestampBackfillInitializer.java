package com.nankai.yuqing.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类结果未携带原帖时间时，使用同帖最早关联评论时间作为最后的补齐依据。
 * 仅补齐空值，不覆盖分类文件中的原帖时间，并同步保持时间戳字段一致。
 */
@Component
@Order(4)
public class PostTimestampBackfillInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PostTimestampBackfillInitializer.class);
    private final ObjectMapper objectMapper;
    private final PostRepository postRepository;
    private final AnalysisService analysisService;

    @Value("${yuqing.comment-file:../comments.json}")
    private String commentFile;

    @Value("${yuqing.classified-results.enabled:false}")
    private boolean classifiedResultsEnabled;

    public PostTimestampBackfillInitializer(ObjectMapper objectMapper,
                                            PostRepository postRepository,
                                            AnalysisService analysisService) {
        this.objectMapper = objectMapper;
        this.postRepository = postRepository;
        this.analysisService = analysisService;
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
            Map<String, TimeValue> earliestByThread = new HashMap<>();
            for (Map<String, Object> comment : comments) {
                String threadId = text(comment.get("thread_id"));
                TimeValue time = parseTime(comment.get("publish_time"));
                if (!threadId.isBlank() && time != null) {
                    earliestByThread.merge(threadId, time,
                        (left, right) -> left.localDateTime().isBefore(right.localDateTime()) ? left : right);
                }
            }
            List<Post> updated = new ArrayList<>();
            for (Post post : postRepository.findAll()) {
                TimeValue inferredTime = earliestByThread.get(post.getId());
                boolean changed = false;
                if (post.getPublishTime() == null && inferredTime != null) {
                    post.setPublishTime(inferredTime.localDateTime());
                    post.setPublishTimestamp(inferredTime.epochSecond());
                    changed = true;
                } else if (post.getPublishTime() != null && post.getPublishTimestamp() == null) {
                    post.setPublishTimestamp(post.getPublishTime()
                        .atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());
                    changed = true;
                }
                if (changed) {
                    updated.add(post);
                }
            }
            if (!updated.isEmpty()) postRepository.saveAll(updated);
            // 已分类数据在上一阶段导入时可能尚无帖子时间。时间补齐后
            // 立即重建事件，同时清理旧版曾经生成的“当天事件”。
            analysisService.aggregateEvents();
            log.info("趋势时间补齐完成：使用关联评论时间补齐 {} 条帖子（可用时间线程 {} 个）",
                updated.size(), earliestByThread.size());
        } catch (Exception e) {
            log.warn("趋势时间补齐失败，保留现有帖子时间，不影响核心功能", e);
        }
    }

    private TimeValue parseTime(Object value) {
        try {
            String time = text(value);
            if (time.isBlank()) return null;
            OffsetDateTime parsed = OffsetDateTime.parse(time);
            return new TimeValue(parsed.toLocalDateTime(), parsed.toEpochSecond());
        } catch (Exception ignored) {
            return null;
        }
    }

    private record TimeValue(LocalDateTime localDateTime, long epochSecond) {}

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
