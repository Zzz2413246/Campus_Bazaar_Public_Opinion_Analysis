package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.model.SafetyRelevance;
import org.springframework.stereotype.Service;

import java.util.*;

/** 末代三阶段流程的 Java 适配：初筛、18类完整分析、风险评估。 */
@Service
public class Task2ClassificationWorkflow {
    public record Result(String postId, String status, String safetyRelevance, String categoryCode,
                         int confidence, String riskLevel, String reason, List<String> evidence,
                         String discussionSummary, List<String> controversies, List<String> safetyClues,
                         String emotion, String riskSource, int analyzedComments,
                         Map<String, CommentResult> comments) {}
    public record CommentResult(String id, boolean safetyRelated, String emotion) {}

    private static final Set<String> RELEVANCE_VALUES = SafetyRelevance.VALUES;
    private static final Set<String> RISK = Set.of("LOW", "MEDIUM", "HIGH");
    private final Task2StructuredLlmClient client;
    private final Task2PromptCatalog prompts;

    public Task2ClassificationWorkflow(Task2StructuredLlmClient client, Task2PromptCatalog prompts) {
        this.client = client; this.prompts = prompts;
    }

    public Result process(Post post, List<PostComment> comments) {
        Map<String, Object> postInput = postInput(post);
        JsonNode postScreen = client.generate(prompts.screenPost(), postInput);
        requireId(post.getId(), postScreen.path("post_id").asText());
        String postRelevance = enumValue(postScreen, "safety_relevance", RELEVANCE_VALUES);
        int screenConfidence = percent(postScreen.path("confidence").asDouble(-1));
        String overall = postRelevance;

        if (SafetyRelevance.UNRELATED.equals(postRelevance) && !comments.isEmpty()) {
            Map<String, Object> commentInput = new LinkedHashMap<>();
            commentInput.put("post_id", post.getId());
            commentInput.put("post_title", clean(post.getTitle()));
            commentInput.put("post_content", clean(post.getContent()));
            commentInput.put("comments", comments.stream().map(this::commentInput).toList());
            JsonNode screened = client.generate(prompts.screenComments(), commentInput);
            requireId(post.getId(), screened.path("post_id").asText());
            Set<String> ids = new HashSet<>();
            boolean safety = false, uncertain = false;
            for (JsonNode item : screened.path("comment_results")) {
                String id = item.path("comment_id").asText();
                if (!ids.add(id)) throw new IllegalArgumentException("评论初筛结果存在重复ID：" + id);
                String relevance = enumValue(item, "safety_relevance", RELEVANCE_VALUES);
                safety |= SafetyRelevance.RELATED.equals(relevance);
                uncertain |= SafetyRelevance.UNCERTAIN.equals(relevance);
            }
            Set<String> sourceIds = comments.stream().map(PostComment::getId).collect(java.util.stream.Collectors.toSet());
            if (!ids.equals(sourceIds)) throw new IllegalArgumentException("评论初筛结果ID与输入不一致");
            overall = safety ? SafetyRelevance.RELATED
                : uncertain ? SafetyRelevance.UNCERTAIN : SafetyRelevance.UNRELATED;
        }

        if (SafetyRelevance.UNRELATED.equals(overall)) {
            return new Result(post.getId(), "SKIPPED_UNRELATED", overall, "not_safety",
                screenConfidence, "LOW", "初筛确认与校园安全无关", List.of(), "", List.of(),
                List.of(), "中性", "POST", comments.size(), Map.of());
        }

        Map<String, Object> fullInput = new LinkedHashMap<>(postInput);
        fullInput.remove("post_id"); fullInput.put("id", post.getId());
        fullInput.put("like_count", safe(post.getLikeCount()));
        fullInput.put("comment_count", safe(post.getCommentCount()));
        fullInput.put("comments", comments.stream().map(this::fullCommentInput).toList());
        JsonNode full = client.generate(prompts.classify(), fullInput);
        requireId(post.getId(), full.path("post_id").asText());
        JsonNode postAnalysis = full.path("post_analysis");
        JsonNode discussion = full.path("discussion_analysis");
        String relevance = deriveRelevance(postAnalysis, full.path("comment_analyses"), discussion);
        String category = postAnalysis.path("safety_category").asText();
        validateCategory(relevance, category);
        validateEvidence(post, postAnalysis.path("evidence_spans"));
        Map<String, CommentResult> commentResults = validateComments(comments, full.path("comment_analyses"));
        validateClusters(commentResults.keySet(), discussion.path("viewpoint_clusters"));

        String risk = "LOW"; String riskSource = deriveRiskSource(postAnalysis, commentResults.values());
        if (SafetyRelevance.RELATED.equals(relevance)) {
            JsonNode assessed = client.generate(prompts.assessRisk(), Map.of("post", fullInput, "analysis", full));
            risk = enumValue(assessed, "risk_level", RISK);
            risk = applyRiskFloor(category, risk, post, comments, commentResults);
        }
        String status = SafetyRelevance.UNCERTAIN.equals(relevance) ? "NEEDS_VERIFICATION" : "ANALYZED";
        return new Result(post.getId(), status, relevance, category,
            percent(postAnalysis.path("confidence").asDouble(screenConfidence / 100.0)), risk,
            postAnalysis.path("reason").asText(), strings(postAnalysis.path("evidence_spans")),
            discussion.path("discussion_summary").asText(), strings(discussion.path("controversies")),
            strings(discussion.path("safety_clues")), deriveEmotion(full.path("comment_analyses")),
            riskSource, comments.size(), commentResults);
    }

    private Map<String,Object> postInput(Post p) {
        Map<String,Object> m=new LinkedHashMap<>(); m.put("post_id",p.getId()); m.put("title",clean(p.getTitle()));
        m.put("content",clean(p.getContent())); m.put("publish_time",Objects.toString(p.getPublishTime(),""));
        m.put("category_name",clean(p.getCategoryName())); return m;
    }
    private Map<String,Object> commentInput(PostComment c) {
        Map<String,Object> m=new LinkedHashMap<>(); m.put("comment_id",c.getId());
        m.put("parent_comment_id",c.getParentCommentId()); m.put("root_comment_id",c.getRootCommentId());
        m.put("content",clean(c.getContent())); return m;
    }
    private Map<String,Object> fullCommentInput(PostComment c) { Map<String,Object> m=new LinkedHashMap<>(commentInput(c)); m.put("publish_time",Objects.toString(c.getPublishTime(),"")); return m; }
    private Map<String,CommentResult> validateComments(List<PostComment> source, JsonNode nodes) {
        Map<String,CommentResult> result=new LinkedHashMap<>();
        for(JsonNode n:nodes){String id=n.path("comment_id").asText(); if(result.containsKey(id)) throw new IllegalArgumentException("完整分析评论ID重复："+id); result.put(id,new CommentResult(id,n.path("is_safety_related").asBoolean(false),sentiment(n.path("sentiment").asText())));}
        Set<String> sourceIds=source.stream().map(PostComment::getId).collect(java.util.stream.Collectors.toSet());
        if(!result.keySet().equals(sourceIds)) throw new IllegalArgumentException("完整分析评论ID与输入不一致"); return result;
    }
    private void validateClusters(Set<String> ids, JsonNode clusters){for(JsonNode c:clusters)for(JsonNode id:c.path("comment_ids"))if(!ids.contains(id.asText()))throw new IllegalArgumentException("观点组引用未知评论ID："+id.asText());}
    private String deriveRelevance(JsonNode post, JsonNode comments, JsonNode discussion){if(SafetyRelevance.RELATED.equals(post.path("safety_relevance").asText())||SafetyRelevance.RELATED.equals(discussion.path("safety_relevance").asText()))return SafetyRelevance.RELATED;for(JsonNode c:comments)if(c.path("is_safety_related").asBoolean())return SafetyRelevance.RELATED;if(SafetyRelevance.UNCERTAIN.equals(post.path("safety_relevance").asText())||SafetyRelevance.UNCERTAIN.equals(discussion.path("safety_relevance").asText()))return SafetyRelevance.UNCERTAIN;return SafetyRelevance.UNRELATED;}
    private void validateCategory(String relevance,String category){if(SafetyRelevance.UNRELATED.equals(relevance)&&!"not_safety".equals(category))throw new IllegalArgumentException("unrelated必须使用not_safety");if(SafetyRelevance.RELATED.equals(relevance)&&(!SafetyCategoryStandard.CODE_TO_NAME.containsKey(category)||"undetermined".equals(category)))throw new IllegalArgumentException("related类别无效："+category);if(SafetyRelevance.UNCERTAIN.equals(relevance)&&!(SafetyCategoryStandard.CODE_TO_NAME.containsKey(category)))throw new IllegalArgumentException("uncertain类别无效："+category);}
    private void validateEvidence(Post post,JsonNode evidence){String title=clean(post.getTitle()),content=clean(post.getContent());for(JsonNode e:evidence){String s=e.asText();if(!s.isBlank()&&!title.contains(s)&&!content.contains(s))throw new IllegalArgumentException("判断依据不在主帖原文中："+s);}}
    private String applyRiskFloor(String category,String risk,Post post,List<PostComment> comments,Map<String,CommentResult> results){String text=clean(post.getTitle())+clean(post.getContent())+comments.stream().filter(c->results.get(c.getId()).safetyRelated()).map(PostComment::getContent).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining());if("mental_crisis".equals(category))risk=higher(risk,"MEDIUM");if(Set.of("fire_electrical","lab_hazard").contains(category)&&contains(text,"冒烟","焦味","起火","着火","火灾","爆炸","刺鼻气味","头晕","呼吸困难","中毒","泄漏"))risk="HIGH";if("public_order".equals(category)&&contains(text,"打架","动手","流血","受伤"))risk=higher(risk,"MEDIUM");return risk;}
    private String deriveRiskSource(JsonNode post,Collection<CommentResult> comments){boolean p=!SafetyRelevance.UNRELATED.equals(post.path("safety_relevance").asText()),c=comments.stream().anyMatch(CommentResult::safetyRelated);return p&&c?"POST_AND_COMMENTS":p?"POST":"COMMENTS";}
    private String deriveEmotion(JsonNode comments){int neg=0,pos=0;for(JsonNode c:comments){String s=c.path("sentiment").asText();neg+=("NEGATIVE".equals(s)?1:0);pos+=("POSITIVE".equals(s)?1:0);}return neg>pos?"负面":pos>neg?"正面":"中性";}
    private String sentiment(String s){return "NEGATIVE".equals(s)?"负面":"POSITIVE".equals(s)?"正面":"中性";}
    private String enumValue(JsonNode n,String f,Set<String> allowed){String v=n.path(f).asText();if(!allowed.contains(v))throw new IllegalArgumentException(f+"取值无效："+v);return v;}
    private void requireId(String expected,String actual){if(!expected.equals(actual))throw new IllegalArgumentException("模型返回ID不一致：expected="+expected+", actual="+actual);}
    private int percent(double v){if(v<0||v>1)throw new IllegalArgumentException("置信度必须在0到1之间");return(int)Math.round(v*100);}
    private List<String> strings(JsonNode n){List<String> out=new ArrayList<>();if(n.isArray())n.forEach(x->out.add(x.asText()));return out;}
    private boolean contains(String text,String...signals){return Arrays.stream(signals).anyMatch(text::contains);}
    private String higher(String a,String b){return order(a)>=order(b)?a:b;} private int order(String r){return "HIGH".equals(r)?2:"MEDIUM".equals(r)?1:0;}
    private int safe(Integer v){return v==null?0:Math.max(0,v);} private String clean(String s){return s==null?"":s.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();}
}
