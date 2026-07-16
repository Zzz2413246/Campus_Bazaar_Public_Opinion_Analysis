package com.nankai.yuqing.config;

import com.nankai.yuqing.service.AnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务配置类
 * 用于定期执行舆情分析和事件聚合任务
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);
    private final AnalysisService analysisService;

    public ScheduledTasks(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 每小时执行一次帖子分析任务
     * cron: "0 0 * * * ?" - 每个小时的第0分钟执行
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledAnalyzePosts() {
        log.info("========== 定时任务开始：分析所有帖子 ==========");
        try {
            analysisService.analyzeAllPosts();
            log.info("========== 定时任务完成：分析所有帖子 ==========");
        } catch (Exception e) {
            log.error("定时任务失败：分析所有帖子", e);
        }
    }

    /**
     * 每小时5分执行一次事件聚合任务
     * cron: "0 5 * * * ?" - 每个小时的第5分钟执行
     * 在分析任务之后执行，确保数据已更新
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void scheduledAggregateEvents() {
        log.info("========== 定时任务开始：聚合事件 ==========");
        try {
            analysisService.aggregateEvents();
            log.info("========== 定时任务完成：聚合事件 ==========");
        } catch (Exception e) {
            log.error("定时任务失败：聚合事件", e);
        }
    }
}
