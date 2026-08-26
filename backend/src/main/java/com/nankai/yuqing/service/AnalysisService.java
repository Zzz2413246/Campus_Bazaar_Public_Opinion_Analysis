package com.nankai.yuqing.service;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostCommentRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 校园安全文本分析服务。
 *
 * <p>规则 2.1.3 的原则是“宁可少报，不把普通交易误报成安全事件”：
 * 使用带权短语、标题加权、来源排除和上下文组合替代原来的单字命中；
 * 事件按安全类别、细分话题和自然周聚合，避免把几十天内不相关的帖子合并。
 * 评论只作为增量佐证：评论不覆盖原帖分类，多条同类证据只生成复核提示；
 * 对已有分类的负面情绪和互动热度只提供有上限的风险加权。</p>
 */
@Service
public class AnalysisService {

    public static final String ANALYSIS_VERSION = "3.0.0-final-standard";
    private static final int CATEGORY_THRESHOLD = 5;
    private static final int FIXED_HIGH_RISK_THRESHOLD = 70;
    private static final int FIXED_MEDIUM_RISK_THRESHOLD = 40;
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final AnalysisSettingsService settingsService;
    private final PostCommentRepository commentRepository;

    @Autowired
    public AnalysisService(PostRepository postRepository,
                           EventRepository eventRepository,
                           AnalysisSettingsService settingsService,
                           PostCommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
        this.commentRepository = commentRepository;
    }

    /** 兼容现有设置测试和独立调用。 */
    public AnalysisService(PostRepository postRepository,
                           EventRepository eventRepository,
                           AnalysisSettingsService settingsService) {
        this(postRepository, eventRepository, settingsService, null);
    }

    /** 单元测试兼容构造器，使用默认阈值和内置分类。 */
    public AnalysisService(PostRepository postRepository, EventRepository eventRepository) {
        this(postRepository, eventRepository, null, null);
    }

    private record CategoryRule(String category, int severity, Map<String, Integer> phrases) {}
    private record Classification(String category, int confidence, int evidenceScore, int severity) {
        static Classification none() { return new Classification(null, 0, 0, 0); }
    }
    private record CommentSignal(
        int total,
        int negative,
        int totalLikes,
        Map<String, Integer> categoryCounts,
        Map<String, Integer> categoryEvidence,
        Map<String, Integer> urgentCounts,
        Map<String, Integer> negativeCategoryCounts,
        Map<String, Integer> severeCounts
    ) {
        static CommentSignal none() {
            return new CommentSignal(
                0, 0, 0, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        int countFor(String category) {
            return category == null ? 0 : categoryCounts.getOrDefault(category, 0);
        }

        int evidenceFor(String category) {
            return category == null ? 0 : categoryEvidence.getOrDefault(category, 0);
        }

        int urgentFor(String category) {
            return category == null ? 0 : urgentCounts.getOrDefault(category, 0);
        }

        int negativeCountFor(String category) {
            return category == null ? 0 : negativeCategoryCounts.getOrDefault(category, 0);
        }

        int severeFor(String category) {
            return category == null ? 0 : severeCounts.getOrDefault(category, 0);
        }
    }

    private static final List<CategoryRule> CATEGORY_RULES = List.of(
        rule("个人安全", 30,
            "持刀", 8, "袭击", 8, "猥亵", 8, "性侵", 8, "性骚扰", 8,
            "尾随", 7, "跟踪", 7, "偷拍", 7, "霸凌", 7, "威胁", 6,
            "恐吓", 6, "人身安全", 6, "持续骚扰", 6, "网络骚扰", 6),
        rule("意外伤害", 28,
            "踩踏", 8, "溺水", 8, "坠落", 8, "跌倒", 6, "摔伤", 6,
            "运动伤害", 6, "物体打击", 7, "挤压受伤", 7, "意外受伤", 6),
        rule("消防与电气安全", 30,
            "火灾", 8, "起火", 8, "着火", 8, "电池爆炸", 8, "燃气泄漏", 8,
            "漏电", 7, "触电", 7, "短路", 6, "冒烟", 6, "飞线充电", 6,
            "违规充电", 6, "消防通道堵塞", 6, "电线裸露", 6, "烧焦", 5,
            "消防隐患", 6, "灭火器", 4, "消防", 3, "电线", 2, "插座", 2,
            "充电", 2, "电池", 2, "电瓶车", 2, "电动车", 1, "明火", 4),
        rule("建筑与设施安全", 22,
            "墙体开裂", 8, "围墙倒塌", 8, "楼梯损坏", 7, "门窗脱落", 7,
            "电梯故障", 7, "体育器械损坏", 7, "设施老化", 6, "施工隐患", 6,
            "漏水", 5, "漏雨", 5, "门锁坏", 5, "空调坏", 5, "热水故障", 5),
        rule("食品与公共卫生", 24,
            "食物中毒", 8, "食品安全", 7, "吃出异物", 7, "吃出虫", 7,
            "吃坏肚子", 7, "腹泻", 6, "变质", 6, "发霉", 6, "过期食品", 7,
            "饮用水污染", 8, "聚集性感染", 8, "传染病", 7, "疫情", 5,
            "后厨卫生", 6, "食堂", 3, "卫生", 3, "异物", 4),
        rule("交通安全", 24,
            "车祸", 8, "交通事故", 8, "撞人", 8, "被撞", 7, "逆行", 6,
            "闯红灯", 6, "超速", 6, "危险驾驶", 7, "违停", 5, "道路隐患", 6,
            "校车", 3, "交通安全", 6, "电动车", 2, "骑行", 2),
        rule("网络与数据安全", 27,
            "诈骗", 8, "电诈", 8, "被骗", 8, "骗子", 4, "骗钱", 8, "骗取", 7,
            "刷单", 8, "杀猪盘", 8, "钓鱼链接", 8, "冒充客服", 8, "盗号", 7,
            "账号盗用", 8, "身份冒用", 7, "隐私泄露", 8, "数据泄露", 8,
            "恶意软件", 7, "网络攻击", 8, "系统攻击", 8, "反诈", 6),
        rule("财产安全", 24,
            "盗窃", 8, "偷窃", 8, "被偷", 7, "失窃", 7, "抢夺", 8,
            "抢劫", 8, "入室盗窃", 8, "故意损坏", 7, "砸坏", 6),
        rule("心理危机", 35,
            "跳楼", 8, "坠楼", 8, "自杀", 8, "轻生", 8, "自伤", 8,
            "结束生命", 8, "不想活", 7, "心理失控", 7, "紧急心理求助", 8),
        rule("实验室安全", 32,
            "实验室爆炸", 8, "化学品泄漏", 8, "生物材料泄漏", 8, "辐射源", 8,
            "气瓶爆炸", 8, "压力容器", 7, "实验室中毒", 8, "实验灼伤", 7,
            "实验废液", 6, "违规实验", 6),
        rule("公共秩序与活动安全", 25,
            "打架", 8, "斗殴", 8, "互殴", 8, "多人冲突", 7, "聚集滋事", 8,
            "起哄冲撞", 7, "扰乱秩序", 7, "活动超员", 7, "人员过密", 6,
            "推挤", 6, "疏散混乱", 7),
        rule("环境安全", 22,
            "空气污染", 7, "水体污染", 7, "土壤污染", 7, "污染排放", 7,
            "危险污染物", 8, "环境异味", 6, "废弃物污染", 7, "环境质量异常", 6),
        rule("自然灾害", 30,
            "暴雨", 6, "洪涝", 8, "台风", 7, "地震", 8, "雷击", 7,
            "滑坡", 8, "泥石流", 8, "极端高温", 6, "极端低温", 6),
        rule("政治与国家安全", 35,
            "恐怖主义", 8, "极端主义", 8, "分裂活动", 8, "校园渗透", 8,
            "间谍", 8, "窃密", 8, "非法情报", 8, "暴力极端", 8),
        rule("仇恨与身份歧视", 26,
            "仇恨言论", 8, "民族歧视", 8, "种族歧视", 8, "宗教歧视", 8,
            "性别歧视", 7, "地域歧视", 7, "残障歧视", 8, "疾病歧视", 7,
            "性取向歧视", 8, "身份歧视", 8),
        rule("校园谣言与声誉风险", 24,
            "校园谣言", 8, "学校谣言", 8, "恶意造谣", 8, "虚假招生", 7,
            "办学资格造假", 8, "恶意传播虚假信息", 8, "引发恐慌", 6)
    );

    private static final Map<String, List<String>> TOPIC_PHRASES = new LinkedHashMap<>();
    static {
        TOPIC_PHRASES.put("刷单兼职诈骗", List.of("刷单", "虚假兼职", "兼职诈骗"));
        TOPIC_PHRASES.put("二手交易诈骗", List.of("收钱不发货", "转账后失联", "交易风险"));
        TOPIC_PHRASES.put("账号与支付安全", List.of("电诈", "盗号", "钓鱼链接", "冒充客服", "银行卡", "支付宝"));
        TOPIC_PHRASES.put("中介与押金", List.of("黑中介", "中介", "押金不退", "押金"));
        TOPIC_PHRASES.put("电动车充电", List.of("电瓶车", "电动车", "飞线充电", "违规充电", "充电"));
        TOPIC_PHRASES.put("电气线路隐患", List.of("漏电", "触电", "短路", "电线", "插座"));
        TOPIC_PHRASES.put("火情与烟雾", List.of("火灾", "起火", "着火", "冒烟", "烧焦"));
        TOPIC_PHRASES.put("消防设施与通道", List.of("消防通道", "灭火器", "消防隐患"));
        TOPIC_PHRASES.put("骚扰与尾随", List.of("性骚扰", "骚扰", "尾随", "跟踪", "猥亵", "偷拍"));
        TOPIC_PHRASES.put("盗窃与财物损失", List.of("盗窃", "偷窃", "被偷", "失窃", "抢劫"));
        TOPIC_PHRASES.put("冲突与暴力", List.of("持刀", "打架", "斗殴", "冲突", "威胁", "恐吓"));
        TOPIC_PHRASES.put("交通事故", List.of("车祸", "交通事故", "撞人", "被撞"));
        TOPIC_PHRASES.put("车辆违规与拥堵", List.of("逆行", "闯红灯", "超速", "违停", "乱停", "拥堵", "堵车"));
        TOPIC_PHRASES.put("供水供电", List.of("停水", "停电", "漏水", "漏雨"));
        TOPIC_PHRASES.put("空调与热水", List.of("空调", "热水"));
        TOPIC_PHRASES.put("网络与门禁", List.of("断网", "网络", "wifi", "门锁"));
        TOPIC_PHRASES.put("宿舍卫生", List.of("蟑螂", "老鼠", "卫生"));
        TOPIC_PHRASES.put("食品卫生与异物", List.of("食物中毒", "食品安全", "异物", "虫", "变质", "发霉", "拉肚子", "腹泻"));
        TOPIC_PHRASES.put("突发伤病", List.of("晕倒", "昏迷", "流血", "受伤", "救护车", "急救"));
        TOPIC_PHRASES.put("人员失联与求助", List.of("失联", "走失", "紧急求助", "救命"));
        TOPIC_PHRASES.put("心理危机", List.of("跳楼", "坠楼", "自杀", "轻生"));
    }

    private static final List<String> NEGATIVE_PHRASES = List.of(
        "非常生气", "太气人", "愤怒", "投诉", "不满", "失望", "恶心", "垃圾",
        "坑人", "被坑", "被骗", "危险", "害怕", "担心", "焦虑", "崩溃",
        "受不了", "无语", "烦死", "离谱", "糟糕", "难受", "恐慌", "不合理"
    );
    private static final List<String> POSITIVE_PHRASES = List.of(
        "非常感谢", "谢谢", "感谢", "满意", "开心", "喜欢", "很棒", "优秀",
        "推荐", "舒服", "幸福", "快乐", "解决了", "已解决", "点赞", "支持"
    );
    private static final List<String> URGENT_PHRASES = List.of(
        "救命", "紧急", "马上", "立即", "报警", "警察", "救护车", "急救",
        "正在发生", "现场", "失联", "走失", "流血", "持刀", "爆炸", "起火"
    );
    private static final List<String> SEVERE_PHRASES = List.of(
        "火灾", "起火", "持刀", "抢劫", "猥亵", "性骚扰", "跳楼", "坠楼",
        "自杀", "轻生", "爆炸", "食物中毒", "救命", "昏迷"
    );
    private static final List<String> ACTUAL_FRAUD_PHRASES = List.of(
        "诈骗", "电诈", "被骗", "骗钱", "骗取", "刷单", "杀猪盘", "钓鱼链接",
        "冒充客服", "盗号", "卷款", "收钱不发货", "转账后失联", "押金不退",
        "虚假兼职", "黑中介", "校园贷", "高利贷", "套现", "防诈骗", "反诈"
    );
    private static final List<String> LOCATIONS = List.of(
        "西门快递点", "菜鸟驿站", "快递点", "西门", "东门", "南门", "北门",
        "津南校区", "八里台校区", "津南", "八里台", "宿舍楼", "宿舍", "食堂",
        "图书馆", "教学楼", "教室", "操场", "体育馆", "实验室", "校医院"
    );

    private static CategoryRule rule(String category, int severity, Object... pairs) {
        Map<String, Integer> phrases = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            phrases.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return new CategoryRule(category, severity, phrases);
    }

    @Transactional
    public void analyzeAllPosts() {
        List<Post> posts = postRepository.findAll();
        List<PostComment> comments = commentRepository == null ? List.of() : commentRepository.findAll();
        Map<String, List<PostComment>> commentsByPost = comments.stream()
            .filter(c -> c.getThreadId() != null && !c.getThreadId().isBlank())
            .collect(Collectors.groupingBy(PostComment::getThreadId));

        log.info("开始使用规则 {} 分析 {} 条帖子和 {} 条评论", ANALYSIS_VERSION, posts.size(), comments.size());
        for (Post post : posts) {
            analyzePost(post, commentsByPost.getOrDefault(post.getId(), List.of()));
        }
        postRepository.saveAll(posts);
        if (commentRepository != null && !comments.isEmpty()) commentRepository.saveAll(comments);
        log.info("帖子分析完成");
    }

    /** 可独立调用，便于任务二扩展和单元测试复用同一份标准化结果。 */
    public void analyzePost(Post post) {
        analyzePost(post, List.of());
    }

    /**
     * 使用关联评论补充分析。空评论列表与 2.0 原帖分析行为一致。
     */
    public void analyzePost(Post post, List<PostComment> comments) {
        String title = normalize(post.getTitle());
        String text = normalize((post.getTitle() == null ? "" : post.getTitle()) + " "
            + (post.getContent() == null ? "" : post.getContent()));

        Classification baseClassification = classifySafety(title, text, post.getCategoryName());
        CommentSignal commentSignal = analyzeComments(
            comments == null ? List.of() : comments, post.getCategoryName());
        // 评论不覆盖原帖分类；只为已有分类加权，并为普通帖子生成待复核提示。
        Classification classification = baseClassification;
        String suggestedCategory = suggestCommentCategory(commentSignal);
        int matchingComments = commentSignal.countFor(classification.category());
        int commentAdjustment = calculateCommentAdjustment(commentSignal, classification.category());

        post.setSafetyCategory(classification.category());
        post.setClassificationConfidence(classification.confidence());
        post.setEmotion(detectEmotion(text));
        post.setLocation(extractLocation(text));
        post.setProblem(extractProblem(classification.category()));
        post.setDemand(extractDemand(text));
        String topicText = baseClassification.category() == null && classification.category() != null
            ? text + " " + comments.stream().map(PostComment::getContent)
                .filter(Objects::nonNull).map(AnalysisService::normalize).collect(Collectors.joining(" "))
            : text;
        post.setTopic(extractTopic(classification.category(), topicText));
        post.setAnalysisVersion(ANALYSIS_VERSION);
        post.setAnalyzedCommentCount(commentSignal.total());
        post.setNegativeCommentCount(commentSignal.negative());
        post.setCommentSafetyCount(matchingComments);
        post.setCommentRiskAdjustment(commentAdjustment);
        post.setCommentSuggestedCategory(
            classification.category() == null ? suggestedCategory : null);
        post.setCommentSuggestionCount(
            classification.category() == null ? commentSignal.countFor(suggestedCategory) : 0);
        post.setCommentSignal(buildCommentSignal(
            commentSignal, classification.category(), suggestedCategory));
        post.setAnalysisBasis(matchingComments > 0 ? "原帖文本+评论佐证" : "原帖文本");

        int score = calculateRiskScore(post, text, classification);
        post.setRiskScore(score);
        post.setRiskLevel(scoreToLevel(score));
    }

    private CommentSignal analyzeComments(List<PostComment> comments, String sourceCategory) {
        if (comments.isEmpty()) return CommentSignal.none();

        int negative = 0;
        int totalLikes = 0;
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        Map<String, Integer> categoryEvidence = new LinkedHashMap<>();
        Map<String, Integer> urgentCounts = new LinkedHashMap<>();
        Map<String, Integer> negativeCategoryCounts = new LinkedHashMap<>();
        Map<String, Integer> severeCounts = new LinkedHashMap<>();

        for (PostComment comment : comments) {
            String text = normalize(comment.getContent());
            String emotion = detectEmotion(text);
            Classification classification = classifySafety("", text, sourceCategory);
            comment.setEmotion(emotion);
            comment.setSafetyCategory(classification.category());
            comment.setEvidenceScore(classification.evidenceScore());
            comment.setAnalysisVersion(ANALYSIS_VERSION);

            if ("负面".equals(emotion)) negative++;
            totalLikes += safe(comment.getLikeCount());
            if (classification.category() != null) {
                String category = classification.category();
                categoryCounts.merge(category, 1, Integer::sum);
                int authorBonus = safe(comment.getIsAuthor()) == 1 ? 2 : 0;
                categoryEvidence.merge(category, classification.evidenceScore() + authorBonus, Integer::sum);
                if (containsAny(text, URGENT_PHRASES)) urgentCounts.merge(category, 1, Integer::sum);
                if ("负面".equals(emotion)) negativeCategoryCounts.merge(category, 1, Integer::sum);
                if (containsAny(text, SEVERE_PHRASES)) severeCounts.merge(category, 1, Integer::sum);
            }
        }

        return new CommentSignal(
            comments.size(), negative, totalLikes,
            Map.copyOf(categoryCounts), Map.copyOf(categoryEvidence),
            Map.copyOf(urgentCounts), Map.copyOf(negativeCategoryCounts),
            Map.copyOf(severeCounts));
    }

    private String suggestCommentCategory(CommentSignal signal) {
        String category = signal.categoryCounts().keySet().stream()
            .max(Comparator
                .comparingInt((String candidate) -> signal.countFor(candidate))
                .thenComparingInt(signal::evidenceFor))
            .orElse(null);
        if (category == null) return null;

        int count = signal.countFor(category);
        int evidence = signal.evidenceFor(category);
        boolean contextConfirmed = signal.negativeCountFor(category) >= 2
            || signal.urgentFor(category) > 0
            || signal.severeFor(category) > 0;
        boolean reliableConsensus = contextConfirmed
            && ((count >= 2 && evidence >= 16) || (count >= 3 && evidence >= 15));
        return reliableConsensus ? category : null;
    }

    private int calculateCommentAdjustment(CommentSignal signal, String category) {
        int corroboration = signal.countFor(category);
        if (corroboration == 0) return 0;

        int score = corroboration >= 10 ? 6 : corroboration >= 5 ? 4 : corroboration >= 2 ? 2 : 1;
        if (signal.total() >= 3) {
            double negativeRatio = (double) signal.negative() / signal.total();
            if (negativeRatio >= 0.6) score += 4;
            else if (negativeRatio >= 0.35) score += 3;
            else if (negativeRatio >= 0.2) score += 1;
        }
        if (signal.totalLikes() >= 100) score += 3;
        else if (signal.totalLikes() >= 30) score += 2;
        else if (signal.totalLikes() >= 10) score += 1;
        if (signal.urgentFor(category) >= 2) score += 2;
        else if (signal.urgentFor(category) == 1) score += 1;
        return Math.min(12, score);
    }

    private String buildCommentSignal(
            CommentSignal signal, String category, String suggestedCategory) {
        if (signal.total() == 0) return "暂无可关联评论";
        int matching = signal.countFor(category);
        double negativeRatio = signal.negative() * 100.0 / signal.total();
        if (category == null && suggestedCategory != null) {
            return String.format(
                "已分析%d条评论；评论提示【%s】%d条，仅供人工复核；负面评论%d条（%.0f%%）",
                signal.total(), suggestedCategory, signal.countFor(suggestedCategory),
                signal.negative(), negativeRatio);
        }
        return String.format(
            "已分析%d条评论；同类风险佐证%d条；负面评论%d条（%.0f%%）",
            signal.total(), matching, signal.negative(), negativeRatio);
    }

    private Classification classifySafety(String title, String text, String sourceCategory) {
        Classification best = Classification.none();
        boolean ordinarySale = "二手闲置".equals(sourceCategory);
        AnalysisSettingsService.Snapshot settings = runtimeSettings();
        String scoringText = text.replace("入室抢劫的爱情", "")
            .replace("抢劫般的爱情", "")
            .replace("卫生纸", "纸巾")
            .replace("骚扰老师", "打扰老师")
            .replace("私发骚扰老师", "私发打扰老师")
            .replace("骚扰老师要求捞", "打扰老师要求捞");

        for (CategoryRule rule : CATEGORY_RULES) {
            if (!settings.categoryEnabled(rule.category())) continue;
            int score = 0;
            boolean strongEvidence = false;
            for (Map.Entry<String, Integer> entry : rule.phrases().entrySet()) {
                String phrase = entry.getKey();
                int weight = entry.getValue();
                if (scoringText.contains(phrase)) {
                    score += weight;
                    if (title.contains(phrase)) score += Math.min(2, weight);
                    if (weight >= 5) strongEvidence = true;
                }
            }

            // 普通二手商品描述里大量出现“转账、充电、电动车、自行车”，没有强证据时不判为安全事件。
            if (ordinarySale && !strongEvidence
                && ("网络与数据安全".equals(rule.category())
                    || "消防与电气安全".equals(rule.category())
                    || "交通安全".equals(rule.category()))) {
                score = 0;
            }
            if (ordinarySale && "网络与数据安全".equals(rule.category())
                && !containsAny(scoringText, ACTUAL_FRAUD_PHRASES)) {
                score = 0;
            }
            if (!strongEvidence && ("消防与电气安全".equals(rule.category())
                || "建筑与设施安全".equals(rule.category()))) {
                score = 0;
            }
            if (!strongEvidence && "食品与公共卫生".equals(rule.category())
                && !containsAny(scoringText, "食堂", "餐厅", "饭菜", "外卖", "食品", "吃", "餐饮", "后厨")) {
                score = 0;
            }
            if (("心理危机".equals(rule.category()) || "意外伤害".equals(rule.category()))
                && !hasActualEmergencyEvidence(scoringText)) {
                score = 0;
            }

            if (score >= CATEGORY_THRESHOLD && score > best.evidenceScore()) {
                int confidence = Math.min(98, 45 + (score - CATEGORY_THRESHOLD) * 7 + (strongEvidence ? 8 : 0));
                best = new Classification(rule.category(), confidence, score, rule.severity());
            }
        }

        // 自定义分类采用用户配置的多字关键词。关键词至少两个字符，避免恢复单字误报问题。
        for (Map.Entry<String, List<String>> custom : settings.categoryRules().entrySet()) {
            if (!settings.categoryEnabled(custom.getKey())) continue;
            int score = 0;
            for (String keyword : custom.getValue()) {
                if (scoringText.contains(keyword)) {
                    score += 6;
                    if (title.contains(keyword)) score += 2;
                }
            }
            if (score >= CATEGORY_THRESHOLD && score > best.evidenceScore()) {
                int confidence = Math.min(95, 62 + (score - CATEGORY_THRESHOLD) * 6);
                best = new Classification(custom.getKey(), confidence, score, 18);
            }
        }
        return best;
    }

    private boolean hasActualEmergencyEvidence(String text) {
        if (containsAny(text, "跳楼", "坠楼", "自杀", "轻生", "昏迷", "晕倒", "流血",
            "救护车", "失联", "走失", "需要急救", "急救人员", "突发事件", "报警", "受伤")) {
            return true;
        }
        return text.contains("爆炸")
            && containsAny(text, "电池", "燃气", "实验室", "起火", "冒烟", "巨响", "现场", "受伤", "消防");
    }

    private String detectEmotion(String text) {
        int negative = scoreEmotion(text, NEGATIVE_PHRASES);
        int positive = scoreEmotion(text, POSITIVE_PHRASES);
        if (negative >= positive + 2) return "负面";
        if (positive >= negative + 2) return "正面";
        return "中性";
    }

    private int scoreEmotion(String text, List<String> phrases) {
        int score = 0;
        for (String phrase : phrases) {
            int from = 0;
            while ((from = text.indexOf(phrase, from)) >= 0) {
                String prefix = text.substring(Math.max(0, from - 2), from);
                if (!prefix.endsWith("不") && !prefix.endsWith("没") && !prefix.endsWith("无")) score += 2;
                from += phrase.length();
            }
        }
        return score;
    }

    private String extractLocation(String text) {
        for (String location : LOCATIONS) {
            if (text.contains(location)) return location;
        }
        return "未明确";
    }

    private String extractProblem(String category) {
        if (category == null) return "一般讨论";
        if (category.contains("网络")) return "网络与数据风险";
        if (category.contains("消防")) return "消防隐患";
        if (category.contains("个人")) return "人身安全问题";
        if (category.contains("财产")) return "财产侵害";
        if (category.contains("交通")) return "交通问题";
        if (category.contains("设施")) return "设施安全隐患";
        if (category.contains("卫生")) return "公共卫生风险";
        return category;
    }

    private String extractDemand(String text) {
        if (containsAny(text, "报警", "救命", "紧急求助", "帮忙", "求助")) return "紧急求助";
        if (containsAny(text, "投诉", "举报", "不满", "反馈")) return "投诉反馈";
        if (containsAny(text, "提醒", "注意", "小心", "避雷", "警惕")) return "安全提醒";
        if (containsAny(text, "建议", "希望", "能不能改善", "应该整改")) return "改进建议";
        if (containsAny(text, "求问", "请问", "有没有人", "怎么办")) return "求助咨询";
        return "信息分享";
    }

    private String extractTopic(String category, String text) {
        if (category == null) return null;
        for (Map.Entry<String, List<String>> entry : TOPIC_PHRASES.entrySet()) {
            if (entry.getValue().stream().anyMatch(text::contains) && topicBelongsToCategory(entry.getKey(), category)) {
                return entry.getKey();
            }
        }
        return category + "相关讨论";
    }

    private boolean topicBelongsToCategory(String topic, String category) {
        if (topic.contains("诈骗") || topic.contains("支付") || topic.contains("中介")) return category.contains("网络");
        if (topic.contains("充电") || topic.contains("电气") || topic.contains("火情") || topic.contains("消防")) return category.contains("消防");
        if (topic.contains("骚扰")) return category.contains("个人");
        if (topic.contains("盗窃")) return category.contains("财产");
        if (topic.contains("冲突")) return category.contains("秩序");
        if (topic.contains("交通") || topic.contains("车辆")) return category.contains("交通");
        if (topic.contains("供水") || topic.contains("空调") || topic.contains("网络") || topic.contains("宿舍")) return category.contains("设施");
        if (topic.contains("食品")) return category.contains("卫生");
        return topic.contains("心理") && category.contains("心理");
    }

    private int calculateRiskScore(Post post, String text, Classification classification) {
        if (classification.category() == null) return 0;

        int score = classification.severity();
        score += Math.min(10, Math.max(0, classification.confidence() - 45) / 5);
        if ("负面".equals(post.getEmotion())) score += 12;
        if (containsAny(text, URGENT_PHRASES)) score += 15;
        if (containsAny(text, SEVERE_PHRASES)) score += 16;
        if (containsAny(text, "已经发生", "现场", "亲眼", "报警了", "送医", "医院")) score += 6;

        int interactions = safe(post.getCommentCount()) + safe(post.getLikeCount());
        if (interactions >= 100) score += 10;
        else if (interactions >= 30) score += 7;
        else if (interactions >= 10) score += 4;
        else if (interactions >= 3) score += 2;

        int views = safe(post.getViewCount());
        if (views >= 5000) score += 10;
        else if (views >= 2000) score += 8;
        else if (views >= 500) score += 5;
        else if (views >= 100) score += 2;

        // 评论只能提供有限增量，原帖分类仍是主评分基础。
        score += safe(post.getCommentRiskAdjustment());
        return Math.min(score, 100);
    }

    private String scoreToLevel(int score) {
        if (score >= FIXED_HIGH_RISK_THRESHOLD) return "高";
        if (score >= FIXED_MEDIUM_RISK_THRESHOLD) return "中";
        return "低";
    }

    private AnalysisSettingsService.Snapshot runtimeSettings() {
        return settingsService == null
            ? AnalysisSettingsService.Snapshot.defaults()
            : settingsService.getSnapshot();
    }

    /**
     * 按“安全类别 + 细分话题 + 自然周”重建事件。ID 由聚合键稳定生成，
     * 因而重复执行不会制造重复事件，已处置状态也可以保留。
     */
    @Transactional
    public void aggregateEvents() {
        List<Post> allPosts = postRepository.findAll();
        Map<String, EventEntity> previous = eventRepository.findAll().stream()
            .collect(Collectors.toMap(EventEntity::getId, e -> e, (a, b) -> a));

        for (Post post : allPosts) post.setEventId(null);
        eventRepository.deleteAll();

        Map<String, List<Post>> grouped = allPosts.stream()
            .filter(this::isEventEligible)
            // 事件时间必须来自真实帖子时间。无时间帖子仍保留在
            // 待复核列表，但不得用系统当前时间伪造事件和日报。
            .filter(post -> post.getPublishTime() != null)
            .collect(Collectors.groupingBy(this::eventGroupKey, LinkedHashMap::new, Collectors.toList()));

        List<EventEntity> events = new ArrayList<>();
        for (Map.Entry<String, List<Post>> entry : grouped.entrySet()) {
            List<Post> posts = entry.getValue();
            posts.sort(Comparator
                .comparing((Post p) -> safe(p.getRiskScore())).reversed()
                .thenComparing(p -> safe(p.getCommentCount()) + safe(p.getLikeCount()), Comparator.reverseOrder()));

            int maxRisk = posts.stream().mapToInt(p -> safe(p.getRiskScore())).max().orElse(0);
            String sourceRisk = aggregateRiskLevel(posts, maxRisk);
            boolean labelRequiresAlert = isAlertRisk(sourceRisk);
            if (posts.size() < 2 && maxRisk < 70 && !labelRequiresAlert) continue;

            int eventScore = calculateEventScore(posts, maxRisk);
            if (eventScore < 30 && !labelRequiresAlert) continue;

            String id = stableEventId(entry.getKey());
            Post top = posts.get(0);
            EventEntity old = previous.get(id);
            EventEntity event = new EventEntity();
            event.setId(id);
            event.setTitle(generateEventTitle(top, posts));
            event.setCategory(Objects.toString(effectiveEventCategory(top), "其他风险"));
            event.setRiskScore(eventScore);
            String eventRiskLevel = aggregateRiskLevel(posts, eventScore);
            event.setRisk(eventRiskLevel);
            event.setPostCount(posts.size());

            int totalViews = posts.stream().mapToInt(p -> safe(p.getViewCount())).sum();
            long negative = posts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
            double negativeRatio = posts.isEmpty() ? 0 : (double) negative / posts.size();
            event.setAffectedRange(generateAffectedRange(totalViews));
            event.setUrgency("高".equals(eventRiskLevel)
                ? "紧急" : "中".equals(eventRiskLevel) ? "关注" : "一般");
            event.setEmotionSummary(String.format("负面占比%.0f%%", negativeRatio * 100));
            event.setSummary(generateEventSummary(top, posts, eventRiskLevel));

            LocalDateTime latest = posts.stream().map(Post::getPublishTime).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElseThrow();
            event.setCreatedAt(latest);
            event.setUpdatedAt(old != null && old.getUpdatedAt() != null
                ? old.getUpdatedAt() : latest);
            event.setStatus(old != null ? old.getStatus()
                : "高".equals(eventRiskLevel) ? "待研判" : "已确认");
            if (old != null) {
                event.setAssignee(old.getAssignee());
                event.setDueAt(old.getDueAt());
                event.setResolution(old.getResolution());
            }
            events.add(event);

            for (Post post : posts) post.setEventId(id);
        }

        eventRepository.saveAll(events);
        postRepository.saveAll(allPosts);
        log.info("事件聚合完成：{} 个安全帖子簇形成 {} 个事件", grouped.size(), events.size());
    }

    private String eventGroupKey(Post post) {
        LocalDate date = Objects.requireNonNull(post.getPublishTime(),
            "事件聚合不接受缺失发布时间的帖子").toLocalDate();
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return Objects.toString(effectiveEventCategory(post), "其他风险")
            + "|" + Objects.toString(post.getTopic(), "综合")
            + "|" + weekYear + "-W" + String.format("%02d", week);
    }

    private boolean isEventEligible(Post post) {
        if ("无关内容".equals(post.getReviewStatus())) return false;
        return effectiveEventCategory(post) != null || isAlertRisk(sourceRiskLevel(post));
    }

    private String effectiveEventCategory(Post post) {
        if (post.getReviewedAt() != null) {
            return post.getReviewedCategory() == null || post.getReviewedCategory().isBlank()
                ? null : post.getReviewedCategory();
        }
        return post.getSafetyCategory();
    }

    private String stableEventId(String key) {
        return "EVT-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8))
            .toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private int calculateEventScore(List<Post> posts, int maxRisk) {
        int score = (int) Math.round(maxRisk * 0.48);
        int size = posts.size();
        if (size >= 30) score += 24;
        else if (size >= 15) score += 20;
        else if (size >= 8) score += 15;
        else if (size >= 4) score += 10;
        else if (size >= 2) score += 6;

        long negative = posts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        double negativeRatio = (double) negative / Math.max(1, size);
        if (negativeRatio >= 0.6) score += 12;
        else if (negativeRatio >= 0.35) score += 8;
        else if (negativeRatio >= 0.15) score += 4;

        int views = posts.stream().mapToInt(p -> safe(p.getViewCount())).sum();
        if (views >= 20000) score += 8;
        else if (views >= 5000) score += 6;
        else if (views >= 1000) score += 3;

        int interactions = posts.stream().mapToInt(p -> safe(p.getCommentCount()) + safe(p.getLikeCount())).sum();
        if (interactions >= 200) score += 6;
        else if (interactions >= 50) score += 4;
        else if (interactions >= 15) score += 2;

        long activeDays = posts.stream().map(Post::getPublishTime).filter(Objects::nonNull)
            .map(LocalDateTime::toLocalDate).distinct().count();
        score += Math.min(6, Math.max(0, (int) activeDays - 1) * 2);
        return Math.min(100, score);
    }

    private String generateEventTitle(Post top, List<Post> posts) {
        String title = normalizeDisplay(top.getTitle());
        if (title.isBlank()) {
            title = Objects.toString(
                top.getTopic(),
                Objects.toString(effectiveEventCategory(top), "风险标签")) + "相关讨论";
        }
        if (title.length() > 28) title = title.substring(0, 28) + "...";
        return title;
    }

    private String generateAffectedRange(int views) {
        if (views > 10000) return "广泛（" + views + "+人次浏览）";
        if (views > 3000) return "较广（" + views + "+人次浏览）";
        if (views > 500) return "一般（" + views + "+人次浏览）";
        return "较小（" + views + "+人次浏览）";
    }

    private String generateEventSummary(Post top, List<Post> posts, String riskLevel) {
        long days = posts.stream().map(Post::getPublishTime).filter(Objects::nonNull)
            .map(LocalDateTime::toLocalDate).distinct().count();
        String topic = Objects.toString(top.getTopic(), "综合问题");
        String excerpt = normalizeDisplay(top.getContent());
        if (excerpt.length() > 80) excerpt = excerpt.substring(0, 80) + "...";
        int analyzedComments = posts.stream().mapToInt(p -> safe(p.getAnalyzedCommentCount())).sum();
        int negativeComments = posts.stream().mapToInt(p -> safe(p.getNegativeCommentCount())).sum();
        String commentSummary = analyzedComments == 0 ? "" : String.format(
            "关联分析 %d 条评论，其中负面评论 %d 条。", analyzedComments, negativeComments);
        return String.format("该事件属于【%s】，细分话题为【%s】，本周期聚合 %d 条、持续 %d 天。%s%s当前最终风险标签为%s风险。",
            Objects.toString(top.getSafetyCategory(), "其他风险"),
            topic, posts.size(), Math.max(1, days),
            excerpt.isBlank() ? "" : "典型内容：" + excerpt + "。", commentSummary,
            riskLevel);
    }

    private String aggregateRiskLevel(List<Post> posts, int fallbackScore) {
        boolean hasHigh = posts.stream()
            .map(this::sourceRiskLevel)
            .anyMatch("高"::equals);
        if (hasHigh) return "高";
        boolean hasMedium = posts.stream()
            .map(this::sourceRiskLevel)
            .anyMatch("中"::equals);
        if (hasMedium) return "中";
        boolean hasLow = posts.stream()
            .map(this::sourceRiskLevel)
            .anyMatch("低"::equals);
        return hasLow ? "低" : scoreToLevel(fallbackScore);
    }

    private String sourceRiskLevel(Post post) {
        if (post.getReviewedRiskLevel() != null) return post.getReviewedRiskLevel();
        return post.getProvidedRiskLevel() != null
            ? post.getProvidedRiskLevel() : post.getRiskLevel();
    }

    private boolean isAlertRisk(String level) {
        return "高".equals(level) || "中".equals(level);
    }

    public List<Map<String, Object>> getRiskReasons(EventEntity event, List<Post> posts) {
        List<Map<String, Object>> reasons = new ArrayList<>();
        int totalPosts = posts.size();
        reasons.add(reason("同类讨论聚合", totalPosts + "条相关讨论", volumePoints(totalPosts)));

        int maxRisk = posts.stream().mapToInt(p -> safe(p.getRiskScore())).max().orElse(0);
        reasons.add(reason("最高单帖风险", maxRisk + "分", "最高贡献约" + Math.round(maxRisk * 0.48) + "分"));

        long negative = posts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        double ratio = posts.isEmpty() ? 0 : (double) negative / posts.size();
        if (ratio >= 0.15) {
            reasons.add(reason("负面情绪集中", String.format("负面占比%.0f%%", ratio * 100),
                ratio >= 0.6 ? "+12分" : ratio >= 0.35 ? "+8分" : "+4分"));
        }

        int totalViews = posts.stream().mapToInt(p -> safe(p.getViewCount())).sum();
        if (totalViews >= 1000) {
            reasons.add(reason("传播影响", totalViews + "人次浏览", totalViews >= 20000 ? "+8分" : totalViews >= 5000 ? "+6分" : "+3分"));
        }

        int analyzedComments = posts.stream().mapToInt(p -> safe(p.getAnalyzedCommentCount())).sum();
        int matchingComments = posts.stream().mapToInt(p -> safe(p.getCommentSafetyCount())).sum();
        int negativeComments = posts.stream().mapToInt(p -> safe(p.getNegativeCommentCount())).sum();
        int maxAdjustment = posts.stream().mapToInt(p -> safe(p.getCommentRiskAdjustment())).max().orElse(0);
        if (analyzedComments > 0 && (matchingComments > 0 || negativeComments > 0)) {
            reasons.add(reason(
                "评论交叉佐证",
                String.format("分析%d条，含%d条同类风险佐证、%d条负面评论",
                    analyzedComments, matchingComments, negativeComments),
                maxAdjustment > 0 ? "单帖最高+" + maxAdjustment + "分" : "+0分"));
        }
        return reasons;
    }

    /**
     * 使用可配置规则返回事件实际命中的预警依据。
     * 预警规则与核心分类评分分离，调整预警敏感度不会改变原始分类结果。
     */
    public List<Map<String, Object>> getAlertTriggers(EventEntity event, List<Post> posts) {
        AnalysisSettingsService.AlertRules rules = runtimeSettings().alertRules();
        List<Map<String, Object>> triggers = new ArrayList<>();

        if ("高".equals(event.getRisk()) || "中".equals(event.getRisk())) {
            Map<String, Object> labelTrigger = new LinkedHashMap<>();
            labelTrigger.put("code", "risk_label");
            labelTrigger.put("reason", "风险标签判定");
            labelTrigger.put("detail", "当前最终判定为" + event.getRisk() + "风险");
            labelTrigger.put("actual", event.getRisk() + "风险");
            labelTrigger.put("threshold", "中风险或高风险");
            labelTrigger.put("unit", "");
            triggers.add(labelTrigger);
        }

        int postCount = posts.size();
        if (postCount >= rules.minPostCount()) {
            triggers.add(trigger(
                "discussion_volume", "同类讨论集中",
                "已聚合 " + postCount + " 条相关帖子",
                postCount, rules.minPostCount(), "条"));
        }

        long negativeCount = posts.stream()
            .filter(post -> "负面".equals(post.getEmotion()))
            .count();
        int negativeRatio = posts.isEmpty()
            ? 0 : (int) Math.round(negativeCount * 100.0 / posts.size());
        if (rules.negativeRatioPercent() > 0
            && negativeRatio >= rules.negativeRatioPercent()) {
            triggers.add(trigger(
                "negative_ratio", "负面情绪集中",
                "负面帖子占比 " + negativeRatio + "%",
                negativeRatio, rules.negativeRatioPercent(), "%"));
        }

        int interactions = posts.stream()
            .mapToInt(post -> safe(post.getCommentCount()) + safe(post.getLikeCount()))
            .sum();
        if (rules.minInteractions() > 0 && interactions >= rules.minInteractions()) {
            triggers.add(trigger(
                "interaction_heat", "互动热度较高",
                "评论与点赞合计 " + interactions + " 次",
                interactions, rules.minInteractions(), "次"));
        }

        int views = posts.stream().mapToInt(post -> safe(post.getViewCount())).sum();
        if (rules.minViews() > 0 && views >= rules.minViews()) {
            triggers.add(trigger(
                "view_reach", "传播范围扩大",
                "累计浏览 " + views + " 人次",
                views, rules.minViews(), "人次"));
        }

        List<LocalDateTime> publishTimes = posts.stream()
            .map(Post::getPublishTime)
            .filter(Objects::nonNull)
            .sorted()
            .toList();
        if (!publishTimes.isEmpty()) {
            LocalDateTime latest = publishTimes.get(publishTimes.size() - 1);
            LocalDateTime windowStart = latest.minusHours(rules.burstWindowHours());
            int burstCount = (int) publishTimes.stream()
                .filter(time -> !time.isBefore(windowStart))
                .count();
            if (burstCount >= rules.burstPostCount()) {
                triggers.add(trigger(
                    "short_term_burst", "短时间讨论突增",
                    "最近 " + rules.burstWindowHours() + " 小时内出现 "
                        + burstCount + " 条相关帖子",
                    burstCount, rules.burstPostCount(), "条"));
            }
        }

        Map.Entry<String, Long> repeatedLocation = posts.stream()
            .map(Post::getLocation)
            .filter(location -> location != null && !location.isBlank())
            .map(String::trim)
            .filter(location -> !Set.of(
                "未明确", "未知", "暂无", "其他", "无", "-").contains(location))
            .collect(Collectors.groupingBy(
                location -> location, LinkedHashMap::new, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
        if (repeatedLocation != null
            && repeatedLocation.getValue() >= rules.repeatedLocationPostCount()) {
            triggers.add(trigger(
                "repeated_location", "同一地点重复反映",
                repeatedLocation.getKey() + " 被连续提及 "
                    + repeatedLocation.getValue() + " 次",
                repeatedLocation.getValue().intValue(),
                rules.repeatedLocationPostCount(), "次"));
        }

        String combinedText = posts.stream()
            .map(post -> normalizeDisplay(
                Objects.toString(post.getTitle(), "") + " "
                    + Objects.toString(post.getContent(), "") + " "
                    + Objects.toString(post.getTopic(), "")))
            .collect(Collectors.joining(" "))
            .toLowerCase(Locale.ROOT);
        List<String> matchedKeywords = rules.urgentKeywords().stream()
            .filter(combinedText::contains)
            .distinct()
            .limit(8)
            .toList();
        if (!matchedKeywords.isEmpty()) {
            Map<String, Object> keywordTrigger = new LinkedHashMap<>();
            keywordTrigger.put("code", "urgent_keyword");
            keywordTrigger.put("reason", "高危信号词命中");
            keywordTrigger.put("detail", "命中：" + String.join("、", matchedKeywords));
            keywordTrigger.put("actual", matchedKeywords.size());
            keywordTrigger.put("threshold", 1);
            keywordTrigger.put("unit", "个");
            triggers.add(keywordTrigger);
        }
        return triggers;
    }

    private Map<String, Object> trigger(String code,
                                        String reason,
                                        String detail,
                                        int actual,
                                        int threshold,
                                        String unit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("reason", reason);
        result.put("detail", detail);
        result.put("actual", actual);
        result.put("threshold", threshold);
        result.put("unit", unit);
        return result;
    }

    private String volumePoints(int size) {
        if (size >= 30) return "+24分";
        if (size >= 15) return "+20分";
        if (size >= 8) return "+15分";
        if (size >= 4) return "+10分";
        if (size >= 2) return "+6分";
        return "+0分";
    }

    private Map<String, Object> reason(String reason, String detail, String score) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reason", reason);
        result.put("detail", detail);
        result.put("score", score);
        return result;
    }

    public List<String> getLastNDays(int n) {
        List<String> days = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");
        for (int i = n - 1; i >= 0; i--) days.add(now.minusDays(i).format(fmt));
        return days;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String normalizeDisplay(String value) {
        if (value == null) return "";
        return value.replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String text, String... phrases) {
        return containsAny(text, Arrays.asList(phrases));
    }

    private static boolean containsAny(String text, Collection<String> phrases) {
        for (String phrase : phrases) if (text.contains(phrase)) return true;
        return false;
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
