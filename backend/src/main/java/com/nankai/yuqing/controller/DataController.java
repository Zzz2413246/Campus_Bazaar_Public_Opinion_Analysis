package com.nankai.yuqing.controller;

import com.nankai.yuqing.service.CommentImportService;
import com.nankai.yuqing.service.DataImportService;
import com.nankai.yuqing.service.ReanalysisJobService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据管理接口
 * - 批量导入API
 * - 数据统计
 * - 重新分析
 * - 数据清理
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private final DataImportService dataImportService;
    private final CommentImportService commentImportService;
    private final ReanalysisJobService reanalysisJobService;

    public DataController(DataImportService dataImportService,
                          CommentImportService commentImportService,
                          ReanalysisJobService reanalysisJobService) {
        this.dataImportService = dataImportService;
        this.commentImportService = commentImportService;
        this.reanalysisJobService = reanalysisJobService;
    }

    /**
     * 批量导入帖子
     * POST /api/data/import
     *
     * @param rawData JSON数组，每个元素包含帖子原始数据
     * @return 导入统计：total（总数）、imported（新导入数）、skipped（跳过数）、errors（错误数）
     */
    @PostMapping("/import")
    public Map<String, Object> importPosts(@RequestBody List<Map<String, Object>> rawData) {
        return dataImportService.importPosts(rawData);
    }

    /**
     * 批量增量导入评论，发生新增或更新后统一重算，确保评论立即参与评判。
     */
    @PostMapping("/comments/import")
    public Map<String, Object> importComments(@RequestBody List<Map<String, Object>> rawData) {
        Map<String, Object> result = new java.util.LinkedHashMap<>(
            commentImportService.importComments(rawData));
        int changed = number(result.get("imported")) + number(result.get("updated"));
        if (changed > 0) {
            result.put("reanalysisJob", reanalysisJobService.start());
            result.put("message", "评论已安全保存，后台重新分析已启动");
        }
        result.put("reanalyzed", false);
        result.put("reanalysisScheduled", changed > 0);
        return result;
    }

    /**
     * 获取数据统计
     * GET /api/data/stats
     *
     * @return 统计信息：帖子总数、事件数量、各分类统计、情绪统计、风险等级统计等
     */
    @GetMapping("/stats")
    public Map<String, Object> getDataStats() {
        return dataImportService.getDataStats();
    }

    /**
     * 重新分析所有数据
     * POST /api/data/reanalyze
     *
     * 重新执行 analyzeAllPosts 和 aggregateEvents
     */
    @PostMapping("/reanalyze")
    public Map<String, Object> reanalyzeAll() {
        return reanalysisJobService.start();
    }

    @GetMapping("/reanalyze/status")
    public Map<String, Object> reanalyzeStatus() {
        return reanalysisJobService.status();
    }

    /**
     * 清空所有数据（谨慎操作）
     * DELETE /api/data/all
     *
     * 清空所有帖子和事件数据
     */
    @DeleteMapping("/all")
    public Map<String, Object> clearAll() {
        dataImportService.clearAll();
        return Map.of("success", true, "message", "所有数据已清空");
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
