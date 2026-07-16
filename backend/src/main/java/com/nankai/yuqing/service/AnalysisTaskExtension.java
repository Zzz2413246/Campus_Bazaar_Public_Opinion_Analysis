package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;

import java.util.List;
import java.util.Map;

/**
 * 后续分析任务的稳定扩展契约。任务二拿到评分标准后只需实现本接口，
 * 无需修改现有帖子分析、数据导入和前端核心接口。
 */
public interface AnalysisTaskExtension {
    String code();
    Map<String, Object> status();
    Map<String, Object> execute(List<Post> posts, Map<String, Object> request);
}
