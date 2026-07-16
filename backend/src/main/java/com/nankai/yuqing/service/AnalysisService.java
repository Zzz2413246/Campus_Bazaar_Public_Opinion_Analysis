package com.nankai.yuqing.service;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
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
 * <p>规则 2.0 的原则是“宁可少报，不把普通交易误报成安全事件”：
 * 使用带权短语、标题加权、来源排除和上下文组合替代原来的单字命中；
 * 事件按安全类别、细分话题和自然周聚合，避免把几十天内不相关的帖子合并。</p>
 */
@Service
public class AnalysisService {

    public static final String ANALYSIS_VERSION = "2.0";
    private static final int CATEGORY_THRESHOLD = 5;
    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final PostRepository postRepository;
    private final EventRepository eventRepository;
    private final AnalysisSettingsService settingsService;

    @Autowired
    public AnalysisService(PostRepository postRepository,
                           EventRepository eventRepository,
                           AnalysisSettingsService settingsService) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
        this.settingsService = settingsService;
    }

    /** 单元测试兼容构造器，使用默认阈值和内置分类。 */
    public AnalysisService(PostRepository postRepository, EventRepository eventRepository) {
        this(postRepository, eventRepository, null);
    }

    private record CategoryRule(String category, int severity, Map<String, Integer> phrases) {}
    private record Classification(String category, int confidence, int evidenceScore, int severity) {
        static Classification none() { return new Classification(null, 0, 0, 0); }
    }

    private static final List<CategoryRule> CATEGORY_RULES = List.of(
        rule("突发事件", 35,
            "救命", 3, "跳楼", 8, "坠楼", 8, "自杀", 8, "轻生", 8, "爆炸", 8,
            "紧急求助", 7, "昏迷", 7, "晕倒", 6, "流血", 6, "救护车", 6,
            "失联", 6, "走失", 6, "需要急救", 6, "急救人员", 6, "突发事件", 6, "报警", 3,
            "受伤", 3, "医院", 2, "急诊", 3),
        rule("消防与用电安全", 28,
            "火灾", 8, "起火", 8, "着火", 8, "电池爆炸", 8, "燃气泄漏", 8,
            "漏电", 7, "触电", 7, "短路", 6, "冒烟", 6, "飞线充电", 6,
            "违规充电", 6, "消防通道堵塞", 6, "电线裸露", 6, "烧焦", 5,
            "消防隐患", 6, "灭火器", 4, "消防", 3, "电线", 2, "插座", 2,
            "充电", 2, "电池", 2, "电瓶车", 2, "电动车", 1, "明火", 4),
        rule("治安与人身安全", 28,
            "持刀", 8, "抢劫", 8, "猥亵", 8, "性骚扰", 8, "打架", 7,
            "斗殴", 7, "尾随", 7, "跟踪", 7, "偷拍", 7, "盗窃", 7,
            "被偷", 6, "偷窃", 6, "威胁", 6, "恐吓", 6, "人身安全", 6,
            "寻衅滋事", 7, "骚扰", 5, "冲突", 3, "争执", 2, "失窃", 5,
            "危险", 3, "报警", 3, "警察", 2, "受伤", 3),
        rule("诈骗与财产安全", 27,
            "诈骗", 8, "电诈", 8, "被骗", 8, "骗子", 4, "骗钱", 8, "骗取", 7,
            "刷单", 8, "杀猪盘", 8, "钓鱼链接", 8, "冒充客服", 8, "盗号", 7,
            "卷款", 7, "跑路", 2, "收钱不发货", 8, "转账后失联", 8,
            "押金不退", 7, "虚假兼职", 7, "黑中介", 7, "校园贷", 8,
            "高利贷", 8, "套现", 6, "防诈骗", 6, "反诈", 6, "转账", 2,
            "退款", 2, "定金", 2, "押金", 2, "中介", 2, "银行卡", 2,
            "支付宝", 2, "微信收款", 2, "交易风险", 4),
        rule("食堂与餐饮问题", 18,
            "食物中毒", 8, "食品安全", 7, "吃出异物", 7, "吃出虫", 7,
            "吃坏肚子", 7, "拉肚子", 6, "腹泻", 6, "变质", 6, "发霉", 6,
            "过期食品", 7, "不卫生", 5, "后厨卫生", 6, "食堂", 3,
            "餐厅", 2, "饭菜", 2, "外卖", 1, "卫生", 3, "异物", 4, "虫子", 4),
        rule("宿舍设施问题", 16,
            "停水", 7, "停电", 7, "漏水", 6, "漏雨", 6, "断网", 5,
            "电梯故障", 7, "门锁坏", 6, "空调坏", 6, "热水故障", 6,
            "墙体开裂", 7, "宿舍维修", 6, "蟑螂", 5, "老鼠", 5,
            "宿舍", 3, "公寓", 2, "空调", 2, "热水", 2, "网络", 1,
            "wifi", 1, "门锁", 2, "宿管", 2, "维修", 2, "故障", 3, "坏了", 3),
        rule("校园交通安全", 20,
            "车祸", 8, "交通事故", 8, "撞人", 8, "被撞", 7, "逆行", 6,
            "闯红灯", 6, "超速", 6, "违停", 5, "乱停", 5, "交通拥堵", 5,
            "占用消防通道", 7, "交通安全", 6, "堵车", 4, "拥堵", 3,
            "电动车", 2, "自行车", 1, "校车", 2, "停车", 2, "骑车", 1)
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
        log.info("开始使用规则 {} 分析 {} 条帖子", ANALYSIS_VERSION, posts.size());
        posts.forEach(this::analyzePost);
        postRepository.saveAll(posts);
        log.info("帖子分析完成");
    }

    /** 可独立调用，便于任务二扩展和单元测试复用同一份标准化结果。 */
    public void analyzePost(Post post) {
        String title = normalize(post.getTitle());
        String text = normalize((post.getTitle() == null ? "" : post.getTitle()) + " "
            + (post.getContent() == null ? "" : post.getContent()));

        Classification classification = classifySafety(title, text, post.getCategoryName());
        post.setSafetyCategory(classification.category());
        post.setClassificationConfidence(classification.confidence());
        post.setEmotion(detectEmotion(text));
        post.setLocation(extractLocation(text));
        post.setProblem(extractProblem(classification.category()));
        post.setDemand(extractDemand(text));
        post.setTopic(extractTopic(classification.category(), text));
        post.setAnalysisVersion(ANALYSIS_VERSION);

        int score = calculateRiskScore(post, text, classification);
        post.setRiskScore(score);
        post.setRiskLevel(scoreToLevel(score));
    }

    private Classification classifySafety(String title, String text, String sourceCategory) {
        Classification best = Classification.none();
        boolean ordinarySale = "二手闲置".equals(sourceCategory);
        AnalysisSettingsService.Snapshot settings = runtimeSettings();
        String scoringText = text.replace("入室抢劫的爱情", "")
            .replace("抢劫般的爱情", "")
            .replace("卫生纸", "纸巾")
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
                && ("诈骗与财产安全".equals(rule.category())
                    || "消防与用电安全".equals(rule.category())
                    || "校园交通安全".equals(rule.category()))) {
                score = 0;
            }
            if (ordinarySale && "诈骗与财产安全".equals(rule.category())
                && !containsAny(scoringText, ACTUAL_FRAUD_PHRASES)) {
                score = 0;
            }
            if (!strongEvidence && ("消防与用电安全".equals(rule.category())
                || "宿舍设施问题".equals(rule.category()))) {
                score = 0;
            }
            if (!strongEvidence && "食堂与餐饮问题".equals(rule.category())
                && !containsAny(scoringText, "食堂", "餐厅", "饭菜", "外卖", "食品", "吃", "餐饮", "后厨")) {
                score = 0;
            }
            if ("突发事件".equals(rule.category()) && !hasActualEmergencyEvidence(scoringText)) {
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
        if (category.contains("诈骗")) return "诈骗风险";
        if (category.contains("消防")) return "消防隐患";
        if (category.contains("治安")) return "治安问题";
        if (category.contains("交通")) return "交通问题";
        if (category.contains("宿舍")) return "设施维修";
        if (category.contains("食堂")) return "餐饮问题";
        if (category.contains("突发")) return "突发事件";
        return "其他";
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
        return switch (category) {
            case "诈骗与财产安全" -> "其他诈骗风险";
            case "消防与用电安全" -> "其他消防隐患";
            case "治安与人身安全" -> "其他治安问题";
            case "校园交通安全" -> "其他交通问题";
            case "宿舍设施问题" -> "其他宿舍设施";
            case "食堂与餐饮问题" -> "其他餐饮问题";
            case "突发事件" -> "其他突发事件";
            default -> category + "相关讨论";
        };
    }

    private boolean topicBelongsToCategory(String topic, String category) {
        if (topic.contains("诈骗") || topic.contains("支付") || topic.contains("中介")) return category.contains("诈骗");
        if (topic.contains("充电") || topic.contains("电气") || topic.contains("火情") || topic.contains("消防")) return category.contains("消防");
        if (topic.contains("骚扰") || topic.contains("盗窃") || topic.contains("冲突")) return category.contains("治安");
        if (topic.contains("交通") || topic.contains("车辆")) return category.contains("交通");
        if (topic.contains("供水") || topic.contains("空调") || topic.contains("网络") || topic.contains("宿舍")) return category.contains("宿舍");
        if (topic.contains("食品")) return category.contains("食堂");
        return (topic.contains("突发") || topic.contains("失联") || topic.contains("心理")) && category.contains("突发");
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

        return Math.min(score, 100);
    }

    private String scoreToLevel(int score) {
        AnalysisSettingsService.Snapshot settings = runtimeSettings();
        if (score >= settings.highThreshold()) return "高";
        if (score >= settings.mediumThreshold()) return "中";
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
            .filter(p -> p.getSafetyCategory() != null)
            .collect(Collectors.groupingBy(this::eventGroupKey, LinkedHashMap::new, Collectors.toList()));

        List<EventEntity> events = new ArrayList<>();
        for (Map.Entry<String, List<Post>> entry : grouped.entrySet()) {
            List<Post> posts = entry.getValue();
            posts.sort(Comparator
                .comparing((Post p) -> safe(p.getRiskScore())).reversed()
                .thenComparing(p -> safe(p.getCommentCount()) + safe(p.getLikeCount()), Comparator.reverseOrder()));

            int maxRisk = posts.stream().mapToInt(p -> safe(p.getRiskScore())).max().orElse(0);
            if (posts.size() < 2 && maxRisk < 70) continue;

            int eventScore = calculateEventScore(posts, maxRisk);
            if (eventScore < 30) continue;

            String id = stableEventId(entry.getKey());
            Post top = posts.get(0);
            EventEntity old = previous.get(id);
            EventEntity event = new EventEntity();
            event.setId(id);
            event.setTitle(generateEventTitle(top, posts));
            event.setCategory(top.getSafetyCategory());
            event.setRiskScore(eventScore);
            event.setRisk(scoreToLevel(eventScore));
            event.setPostCount(posts.size());

            int totalViews = posts.stream().mapToInt(p -> safe(p.getViewCount())).sum();
            long negative = posts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
            double negativeRatio = posts.isEmpty() ? 0 : (double) negative / posts.size();
            event.setAffectedRange(generateAffectedRange(totalViews));
            event.setUrgency(eventScore >= 70 ? "紧急" : eventScore >= 40 ? "关注" : "一般");
            event.setEmotionSummary(String.format("负面占比%.0f%%", negativeRatio * 100));
            event.setSummary(generateEventSummary(top, posts, eventScore));

            LocalDateTime latest = posts.stream().map(Post::getPublishTime).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(LocalDateTime.now());
            event.setCreatedAt(latest);
            event.setUpdatedAt(latest);
            event.setStatus(old != null ? old.getStatus() : eventScore >= 70 ? "待研判" : "已确认");
            events.add(event);

            for (Post post : posts) post.setEventId(id);
        }

        eventRepository.saveAll(events);
        postRepository.saveAll(allPosts);
        log.info("事件聚合完成：{} 个安全帖子簇形成 {} 个事件", grouped.size(), events.size());
    }

    private String eventGroupKey(Post post) {
        LocalDate date = post.getPublishTime() == null ? LocalDate.now() : post.getPublishTime().toLocalDate();
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return post.getSafetyCategory() + "|" + Objects.toString(post.getTopic(), "综合")
            + "|" + weekYear + "-W" + String.format("%02d", week);
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
        if (title.isBlank()) title = Objects.toString(top.getTopic(), top.getSafetyCategory()) + "相关讨论";
        if (title.length() > 28) title = title.substring(0, 28) + "...";
        return title;
    }

    private String generateAffectedRange(int views) {
        if (views > 10000) return "广泛（" + views + "+人次浏览）";
        if (views > 3000) return "较广（" + views + "+人次浏览）";
        if (views > 500) return "一般（" + views + "+人次浏览）";
        return "较小（" + views + "+人次浏览）";
    }

    private String generateEventSummary(Post top, List<Post> posts, int score) {
        long days = posts.stream().map(Post::getPublishTime).filter(Objects::nonNull)
            .map(LocalDateTime::toLocalDate).distinct().count();
        String topic = Objects.toString(top.getTopic(), "综合问题");
        String excerpt = normalizeDisplay(top.getContent());
        if (excerpt.length() > 80) excerpt = excerpt.substring(0, 80) + "...";
        return String.format("该事件属于【%s】，细分话题为【%s】，本周期聚合 %d 条、持续 %d 天。%s综合风险评分 %d 分，属于%s风险事件。",
            top.getSafetyCategory(), topic, posts.size(), Math.max(1, days),
            excerpt.isBlank() ? "" : "典型内容：" + excerpt + "。", score, scoreToLevel(score));
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
        return reasons;
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
