package com.nankai.yuqing.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
import com.nankai.yuqing.service.AnalysisService;
import com.nankai.yuqing.service.DataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 启动时同步外部帖子数据。数据库已有数据时仍会按帖子 ID 做增量导入，
 * 因此替换为更大的数据文件后无需清空原数据库。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final DataImportService dataImportService;
    private final PostRepository postRepository;
    private final ObjectMapper objectMapper;

    @Value("${yuqing.data-file:../posts(2).json}")
    private String dataFile;

    public DataInitializer(DataImportService dataImportService,
                           PostRepository postRepository,
                           ObjectMapper objectMapper) {
        this.dataImportService = dataImportService;
        this.postRepository = postRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        try {
            List<Map<String, Object>> rawData = loadData();
            if (rawData == null || rawData.isEmpty()) {
                log.warn("未找到可用的数据文件，跳过启动同步");
                return;
            }

            Map<String, Object> result = dataImportService.importPosts(rawData);
            int imported = ((Number) result.getOrDefault("imported", 0)).intValue();
            log.info("启动数据同步完成：{}", result);

            // 数据没有新增，但规则版本发生变化时仍自动重算一次。
            if (imported == 0 && hasOutdatedAnalysis()) {
                log.info("检测到旧版分析结果，开始升级到规则 {}", AnalysisService.ANALYSIS_VERSION);
                dataImportService.reanalyzeAll();
            }
        } catch (Exception e) {
            log.error("数据初始化失败", e);
        }
    }

    private boolean hasOutdatedAnalysis() {
        for (Post post : postRepository.findAll()) {
            if (!AnalysisService.ANALYSIS_VERSION.equals(post.getAnalysisVersion())) return true;
        }
        return false;
    }

    private List<Map<String, Object>> loadData() throws Exception {
        // 配置文件优先；兼容项目中新旧两个默认文件名。
        for (String path : List.of(dataFile, "../posts(2).json", "../posts.json")) {
            File file = new File(path);
            if (file.isFile()) {
                log.info("读取数据文件：{}", file.getCanonicalPath());
                return objectMapper.readValue(file, new TypeReference<>() {});
            }
        }

        try (InputStream input = new ClassPathResource("posts.json").getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {});
        }
    }
}
