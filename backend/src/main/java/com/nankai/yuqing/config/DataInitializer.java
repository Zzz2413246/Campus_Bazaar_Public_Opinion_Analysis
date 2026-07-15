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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 启动时加载 posts.json 并执行分析
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final PostRepository postRepository;
    private final AnalysisService analysisService;
    private final ObjectMapper objectMapper;

    public DataInitializer(PostRepository postRepository, AnalysisService analysisService, ObjectMapper objectMapper) {
        this.postRepository = postRepository;
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
    }

    @Value("${yuqing.data-file:../posts.json}")
    private String dataFile;

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> rawData = loadData();
            if (rawData == null || rawData.isEmpty()) {
                log.warn("未找到数据文件，跳过初始化");
                return;
            }

            log.info("开始加载 {} 条原始数据", rawData.size());

            for (Map<String, Object> item : rawData) {
                Post post = convertToPost(item);
                if (post != null) {
                    postRepository.save(post);
                }
            }

            // 执行分析（分类、情绪、风险评分）
            analysisService.analyzeAllPosts();
            // 聚合事件
            analysisService.aggregateEvents();

            long total = postRepository.count();
            log.info("数据加载完成，共 {} 条帖子", total);

        } catch (Exception e) {
            log.error("数据初始化失败", e);
        }
    }

    private List<Map<String, Object>> loadData() throws Exception {
        // 尝试从外部文件加载
        File file = new File(dataFile);
        if (file.exists()) {
            return objectMapper.readValue(file, new TypeReference<>() {});
        }
        // 尝试从 classpath 加载
        try (InputStream is = new ClassPathResource("posts.json").getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<>() {});
        }
    }

    private Post convertToPost(Map<String, Object> item) {
        try {
            Post post = new Post();
            post.setId(String.valueOf(item.get("id")));
            post.setTitle((String) item.get("title"));
            post.setContent((String) item.get("content"));
            post.setAuthor((String) item.get("author"));
            post.setAuthorAvatar((String) item.get("author_avatar"));

            // 解析时间
            String timeStr = (String) item.get("publish_time");
            if (timeStr != null) {
                try {
                    OffsetDateTime odt = OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    post.setPublishTime(odt.toLocalDateTime());
                } catch (Exception e) {
                    post.setPublishTime(LocalDateTime.now());
                }
            }

            Object ts = item.get("publish_timestamp");
            if (ts instanceof Number) {
                post.setPublishTimestamp(((Number) ts).longValue());
            }

            post.setCategoryId((String) item.get("category_id"));
            post.setCategoryName((String) item.get("category_name"));

            post.setCommentCount(toInt(item.get("comment_count")));
            post.setLikeCount(toInt(item.get("like_count")));
            post.setViewCount(toInt(item.get("view_count")));

            Object anon = item.get("is_anonymous");
            if (anon instanceof Number) {
                post.setIsAnonymous(((Number) anon).intValue());
            }

            // 图片URL列表
            Object imgs = item.get("image_urls");
            if (imgs instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> imgList = (List<String>) imgs;
                post.setImageUrls(imgList);
                post.setImageUrlsStr(String.join(";", imgList));
            }

            return post;
        } catch (Exception e) {
            log.warn("转换帖子失败: {}", item.get("id"), e);
            return null;
        }
    }

    private Integer toInt(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return 0;
    }
}
