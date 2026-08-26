package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.model.SafetyRelevance;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 执行并持久化任务二实时分类，人工复核字段始终保持不变。 */
@Service
public class Task2RealtimeClassificationService {
    public static final String VERSION = "task2-final-2026-08";
    private final Task2ClassificationWorkflow workflow;
    private final Task2StructuredLlmClient client;
    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final AnalysisService analysisService;

    public Task2RealtimeClassificationService(Task2ClassificationWorkflow workflow,
                                              Task2StructuredLlmClient client,
                                              PostRepository posts,
                                              PostCommentRepository comments,
                                              AnalysisService analysisService) {
        this.workflow = workflow; this.client = client; this.posts = posts;
        this.comments = comments; this.analysisService = analysisService;
    }

    public boolean enabled() { return client.enabled(); }
    public String modelName() { return client.modelName(); }

    @Transactional
    public Map<String,Object> classify(List<Post> selected, boolean dryRun) {
        List<Map<String,Object>> accepted = new ArrayList<>();
        List<Map<String,String>> failed = new ArrayList<>();
        List<Post> changedPosts = new ArrayList<>();
        List<PostComment> changedComments = new ArrayList<>();
        for (Post post : selected) {
            try {
                List<PostComment> postComments = comments.findByThreadIdOrderByPublishTimeAsc(post.getId());
                Task2ClassificationWorkflow.Result result = workflow.process(post, postComments);
                accepted.add(summary(result));
                if (!dryRun) {
                    apply(post, postComments, result);
                    changedPosts.add(post); changedComments.addAll(postComments);
                }
            } catch (Exception ex) {
                failed.add(Map.of("postId", post.getId(), "error", safeMessage(ex)));
            }
        }
        if (!dryRun) {
            if (!changedPosts.isEmpty()) posts.saveAll(changedPosts);
            if (!changedComments.isEmpty()) comments.saveAll(changedComments);
            analysisService.aggregateEvents();
        }
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("requested", selected.size()); response.put("succeeded", accepted.size());
        response.put("failed", failed.size()); response.put("dryRun", dryRun);
        response.put("results", accepted); response.put("errors", failed);
        return response;
    }

    private void apply(Post post, List<PostComment> commentList, Task2ClassificationWorkflow.Result r) {
        boolean nonSafety = SafetyRelevance.isUnrelated(r.safetyRelevance());
        post.setSafetyCategory(nonSafety ? null : SafetyCategoryStandard.fromExternal(r.categoryCode(), r.safetyRelevance()));
        post.setSafetyRelevance(r.safetyRelevance()); post.setProcessingStatus(r.status());
        post.setClassificationConfidence(r.confidence()); post.setProvidedRiskLevel(riskName(r.riskLevel()));
        post.setRiskLevel(riskName(r.riskLevel())); post.setRiskScore(riskScore(r.riskLevel()));
        post.setAnalysisReason(r.reason()); post.setEvidenceSpans(String.join("；", r.evidence()));
        post.setDiscussionSummary(r.discussionSummary()); post.setControversies(String.join("；", r.controversies()));
        post.setSafetyClues(String.join("；", r.safetyClues())); post.setEmotion(r.emotion());
        post.setAnalyzedCommentCount(r.analyzedComments());
        post.setCommentSafetyCount((int) r.comments().values().stream().filter(Task2ClassificationWorkflow.CommentResult::safetyRelated).count());
        post.setAnalysisBasis("POST".equals(r.riskSource()) ? "原帖文本" : "COMMENTS".equals(r.riskSource()) ? "评论线索" : "原帖文本+评论佐证");
        post.setProblem(nonSafety ? "非安全内容" : "实时分类：" + post.getSafetyCategory());
        post.setTopic(nonSafety ? null : post.getSafetyCategory()); post.setAnalysisVersion(VERSION); post.setEventId(null);
        for (PostComment comment : commentList) {
            Task2ClassificationWorkflow.CommentResult cr = r.comments().get(comment.getId());
            if (cr == null) continue;
            comment.setEmotion(cr.emotion());
            comment.setSafetyCategory(cr.safetyRelated() ? post.getSafetyCategory() : null);
            comment.setEvidenceScore(cr.safetyRelated() ? 100 : 0); comment.setAnalysisVersion(VERSION);
        }
    }

    private Map<String,Object> summary(Task2ClassificationWorkflow.Result r) {
        Map<String,Object> m=new LinkedHashMap<>(); m.put("postId",r.postId()); m.put("status",r.status());
        m.put("safetyRelevance",r.safetyRelevance()); m.put("categoryCode",r.categoryCode());
        m.put("category",SafetyCategoryStandard.fromExternal(r.categoryCode(),r.safetyRelevance()));
        m.put("confidence",r.confidence()); m.put("riskLevel",riskName(r.riskLevel()));
        m.put("riskSource",r.riskSource()); m.put("reason",r.reason()); return m;
    }
    private int riskScore(String risk){return "HIGH".equals(risk)?85:"MEDIUM".equals(risk)?55:20;}
    private String riskName(String risk){return "HIGH".equals(risk)?"高":"MEDIUM".equals(risk)?"中":"低";}
    private String safeMessage(Exception ex){String m=ex.getMessage();return m==null||m.isBlank()?ex.getClass().getSimpleName():m.substring(0,Math.min(500,m.length()));}
}
