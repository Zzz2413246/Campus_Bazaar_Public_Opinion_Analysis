package com.nankai.yuqing.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import com.nankai.yuqing.service.CommentImportService;
import com.nankai.yuqing.service.DataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 帖子增量同步完成后，再同步评论并按需触发一次统一分析。
 */
@Component
@Order(2)
public class CommentDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CommentDataInitializer.class);
    private final CommentImportService commentImportService;
    private final DataImportService dataImportService;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    @Value("${yuqing.comment-file:../comments.json}")
    private String commentFile;

    @Value("${yuqing.classified-results.enabled:false}")
    private boolean classifiedResultsEnabled;

    public CommentDataInitializer(CommentImportService commentImportService,
                                  DataImportService dataImportService,
                                  PostRepository postRepository,
                                  ObjectMapper objectMapper) {
        this.commentImportService = commentImportService;
        this.dataImportService = dataImportService;
        this.postRepository = postRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        if (classifiedResultsEnabled) {
            log.info("已启用外部最终分类数据，评论依据由分类结果同步，跳过旧评论文件");
            return;
        }
        boolean commentsChanged = false;
        try {
            File file = new File(commentFile);
            if (file.isFile()) {
                log.info("读取评论数据文件：{}", file.getCanonicalPath());
                List<Map<String, Object>> rawData =
                    objectMapper.readValue(file, new TypeReference<>() {});
                Map<String, Object> result = commentImportService.importComments(rawData);
                int imported = number(result.get("imported"));
                int updated = number(result.get("updated"));
                commentsChanged = imported + updated > 0;
                log.info("启动评论同步完成：{}", result);
            } else {
                log.info("未找到评论数据文件 {}，保留原有帖子分析逻辑", file.getPath());
            }
        } catch (Exception e) {
            log.error("评论数据初始化失败，帖子核心功能不受影响", e);
        }

        if (commentsChanged || hasOutdatedAnalysis() || commentImportService.hasUnappliedLinks()) {
            log.info("检测到评论变化或旧版结果，开始使用规则 {} 统一分析", AnalysisService.ANALYSIS_VERSION);
            dataImportService.reanalyzeAll();
        }
    }

    private boolean hasOutdatedAnalysis() {
        for (Post post : postRepository.findAll()) {
            if (!AnalysisService.ANALYSIS_VERSION.equals(post.getAnalysisVersion())) return true;
        }
        return false;
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
