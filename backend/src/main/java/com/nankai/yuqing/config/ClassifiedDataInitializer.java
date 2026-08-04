package com.nankai.yuqing.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.service.ClassifiedResultImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 启动时读取全部已经完成分类的结果，并同步安全与待核实数据。 */
@Component
@Order(3)
public class ClassifiedDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ClassifiedDataInitializer.class);
    private final ObjectMapper objectMapper;
    private final ClassifiedResultImportService importService;

    @Value("${yuqing.classified-results.enabled:true}")
    private boolean enabled;

    @Value("${yuqing.classified-results.files:../500.json,../1000.json,../1500.json,../2000.json,../3000.json}")
    private String files;

    public ClassifiedDataInitializer(ObjectMapper objectMapper,
                                     ClassifiedResultImportService importService) {
        this.objectMapper = objectMapper;
        this.importService = importService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!enabled) return;
        List<JsonNode> results = new ArrayList<>();
        for (String path : files.split(",")) {
            File file = new File(path.trim());
            if (!file.isFile()) {
                log.warn("未找到已分类数据文件：{}", file.getPath());
                continue;
            }
            JsonNode root = objectMapper.readTree(file);
            JsonNode fileResults = root.path("results");
            if (!fileResults.isArray()) {
                log.warn("已分类数据文件缺少 results 数组：{}", file.getCanonicalPath());
                continue;
            }
            fileResults.forEach(results::add);
            log.info("读取已分类数据文件：{}（{} 条）", file.getCanonicalPath(), fileResults.size());
        }
        if (results.isEmpty()) {
            log.warn("没有可同步的已分类数据，保留现有数据库");
            return;
        }
        log.info("已分类数据同步完成：{}", importService.synchronize(results));
    }
}
