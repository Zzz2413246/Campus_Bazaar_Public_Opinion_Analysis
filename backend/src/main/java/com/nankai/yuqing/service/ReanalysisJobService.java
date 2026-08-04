package com.nankai.yuqing.service;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 单实例重新分析任务，避免长请求超时和重复并发重算。 */
@Service
public class ReanalysisJobService {

    private final DataImportService dataImportService;
    @Value("${yuqing.classified-results.enabled:true}")
    private boolean externalClassifiedMode;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "yuqing-reanalysis");
        thread.setDaemon(true);
        return thread;
    });
    private volatile JobState current = JobState.idle();

    public ReanalysisJobService(DataImportService dataImportService) {
        this.dataImportService = dataImportService;
    }

    public synchronized Map<String, Object> start() {
        if ("RUNNING".equals(current.status())) return jobMap();
        String id = UUID.randomUUID().toString();
        current = new JobState(id, "RUNNING", 5, "任务已创建", Instant.now(), null, null);
        executor.submit(() -> run(id));
        return jobMap();
    }

    public Map<String, Object> status() {
        return jobMap();
    }

    private Map<String, Object> jobMap() {
        Map<String, Object> map = new LinkedHashMap<>(current.toMap());
        map.put("mode", externalClassifiedMode ? "EXTERNAL_CLASSIFIED" : "LOCAL_RULES");
        return map;
    }

    private void run(String id) {
        try {
            update(id, 15, "正在读取帖子和评论");
            if (externalClassifiedMode) {
                update(id, 45, "正在保留最终分类并刷新事件聚合");
                dataImportService.refreshEventAggregates();
            } else {
                update(id, 35, "正在重新计算分类、情绪和风险");
                dataImportService.reanalyzeAll();
            }
            update(id, 90, "正在汇总统计结果");
            Map<String, Object> result = new LinkedHashMap<>(dataImportService.getDataStats());
            current = new JobState(id, "COMPLETED", 100,
                externalClassifiedMode ? "事件聚合刷新完成，最终分类未被修改" : "重新分析完成",
                current.startedAt(), Instant.now(), result);
        } catch (Exception ex) {
            current = new JobState(id, "FAILED", current.progress(),
                "重新分析失败：" + ex.getMessage(), current.startedAt(), Instant.now(), null);
        }
    }

    private void update(String id, int progress, String message) {
        JobState state = current;
        if (id.equals(state.id())) {
            current = new JobState(id, "RUNNING", progress, message, state.startedAt(), null, null);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private record JobState(
        String id, String status, int progress, String message,
        Instant startedAt, Instant finishedAt, Map<String, Object> result
    ) {
        static JobState idle() {
            return new JobState("", "IDLE", 0, "暂无运行中的任务", null, null, null);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("status", status);
            map.put("progress", progress);
            map.put("message", message);
            map.put("startedAt", startedAt);
            map.put("finishedAt", finishedAt);
            map.put("result", result);
            return map;
        }
    }
}
