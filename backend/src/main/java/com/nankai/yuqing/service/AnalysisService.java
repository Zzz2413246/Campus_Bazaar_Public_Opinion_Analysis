package com.nankai.yuqing.service;

import com.nankai.yuqing.model.EventEntity;
import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.EventRepository;
import com.nankai.yuqing.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 舆情分析服务
 * - 校园安全议题分类（基于关键词规则）
 * - 情绪识别
 * - 风险评分
 * - 事件聚合（按安全类别+关键词共现）
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
    private final PostRepository postRepository;
    private final EventRepository eventRepository;

    public AnalysisService(PostRepository postRepository, EventRepository eventRepository) {
        this.postRepository = postRepository;
        this.eventRepository = eventRepository;
    }

    // 校园安全议题分类关键词表
    private static final Map<String, String[]> SAFETY_KEYWORDS = new LinkedHashMap<>();
    static {
        SAFETY_KEYWORDS.put("诈骗与财产安全", new String[]{"诈骗", "骗", "被骗", "转账", "钱", "二手", "出", "售", "卖", "买", "假", "退款", "刷单", "兼职", "中介", "收费"});
        SAFETY_KEYWORDS.put("消防与用电安全", new String[]{"火", "起火", "冒烟", "充电", "电瓶车", "电动车", "电池", "插座", "漏电", "电线", "烧", "烟"});
        SAFETY_KEYWORDS.put("治安与人身安全", new String[]{"打", "骂", "伤", "危险", "跟踪", "骚扰", "偷", "盗", "丢失", "争执", "冲突", "打架"});
        SAFETY_KEYWORDS.put("校园交通安全", new String[]{"交通", "堵", "车祸", "事故", "撞", "电动车", "自行车", "停车", "违停", "乱停", "拥堵"});
        SAFETY_KEYWORDS.put("宿舍设施问题", new String[]{"宿舍", "空调", "修", "坏", "停水", "停电", "漏水", "热水", "网络", "wifi", "门", "锁", "宿管"});
        SAFETY_KEYWORDS.put("食堂与餐饮问题", new String[]{"食堂", "饭", "吃", "卫生", "涨价", "难吃", "外卖", "食物", "拉肚子", "虫"});
        SAFETY_KEYWORDS.put("突发事件", new String[]{"紧急", "突发", "报警", "警察", "救护", "晕倒", "受伤", "流血"});
    }

    // 负面情绪关键词
    private static final String[] NEGATIVE_WORDS = {"气", "怒", "骂", "烦", "差", "坑", "骗", "危险", "怕", "担心", "投诉", "垃圾", "恶心", "受不了", "无语", "郁闷", "崩溃", "烦死"};
    // 正面情绪关键词
    private static final String[] POSITIVE_WORDS = {"开心", "喜欢", "好", "赞", "感谢", "谢谢", "棒", "优秀", "满意", "幸福", "快乐", "推荐", "舒服"};

    // 地点关键词
    private static final String[] LOCATIONS = {"西门", "东门", "南门", "北门", "津南", "八里台", "宿舍", "食堂", "图书馆", "教室", "教学楼", "操场", "体育馆", "驿站", "快递点"};

    /**
     * 分析所有帖子
     */
    public void analyzeAllPosts() {
        List<Post> posts = postRepository.findAll();
        log.info("开始分析 {} 条帖子", posts.size());

        for (Post post : posts) {
            analyzePost(post);
            postRepository.save(post);
        }
        log.info("分析完成");
    }

    /**
     * 分析单条帖子
     */
    public void analyzePost(Post post) {
        String text = (post.getTitle() != null ? post.getTitle() : "") + " " + (post.getContent() != null ? post.getContent() : "");
        text = text.toLowerCase();

        // 1. 安全议题分类
        post.setSafetyCategory(classifySafety(text));

        // 2. 情绪识别
        post.setEmotion(detectEmotion(text));

        // 3. 提取地点
        post.setLocation(extractLocation(text));

        // 4. 识别问题类型
        post.setProblem(extractProblem(text, post.getSafetyCategory()));

        // 5. 识别诉求
        post.setDemand(extractDemand(text));

        // 6. 风险评分
        int score = calculateRiskScore(post, text);
        post.setRiskScore(score);
        post.setRiskLevel(scoreToLevel(score));
    }

    private String classifySafety(String text) {
        for (Map.Entry<String, String[]> entry : SAFETY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (text.contains(kw)) {
                    return entry.getKey();
                }
            }
        }
        return null; // 非安全相关
    }

    private String detectEmotion(String text) {
        int neg = 0, pos = 0;
        for (String w : NEGATIVE_WORDS) {
            if (text.contains(w)) neg++;
        }
        for (String w : POSITIVE_WORDS) {
            if (text.contains(w)) pos++;
        }
        if (neg > pos) return "负面";
        if (pos > neg) return "正面";
        return "中性";
    }

    private String extractLocation(String text) {
        for (String loc : LOCATIONS) {
            if (text.contains(loc)) return loc;
        }
        return "未明确";
    }

    private String extractProblem(String text, String category) {
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
        if (text.contains("投诉") || text.contains("不满")) return "投诉反馈";
        if (text.contains("求") || text.contains("帮") || text.contains("问")) return "求助咨询";
        if (text.contains("建议") || text.contains("希望")) return "改进建议";
        if (text.contains("提醒") || text.contains("注意") || text.contains("小心")) return "安全提醒";
        return "信息分享";
    }

    /**
     * 风险评分规则
     */
    private int calculateRiskScore(Post post, String text) {
        int score = 0;

        // 讨论量得分
        int interactions = post.getCommentCount() + post.getLikeCount();
        if (interactions > 20) score += 25;
        else if (interactions > 10) score += 15;
        else if (interactions > 5) score += 8;

        // 浏览量得分
        if (post.getViewCount() > 500) score += 15;
        else if (post.getViewCount() > 200) score += 8;
        else if (post.getViewCount() > 50) score += 3;

        // 负面情绪得分
        if ("负面".equals(post.getEmotion())) score += 15;

        // 事件类型得分（高风险类型）
        String cat = post.getSafetyCategory();
        if (cat != null) {
            if (cat.contains("诈骗") || cat.contains("消防") || cat.contains("突发") || cat.contains("治安")) {
                score += 20;
            } else if (cat.contains("交通")) {
                score += 10;
            } else {
                score += 5;
            }
        }

        // 紧急关键词
        if (text.contains("紧急") || text.contains("报警") || text.contains("救命")) score += 15;

        return Math.min(score, 100);
    }

    private String scoreToLevel(int score) {
        if (score >= 70) return "高";
        if (score >= 40) return "中";
        return "低";
    }

    /**
     * 事件聚合 · 按安全类别聚合相关帖子
     */
    public void aggregateEvents() {
        List<Post> posts = postRepository.findAll().stream()
            .filter(p -> p.getSafetyCategory() != null)
            .collect(Collectors.toList());

        // 按安全类别分组
        Map<String, List<Post>> grouped = posts.stream()
            .collect(Collectors.groupingBy(Post::getSafetyCategory));

        int eventId = 1;
        for (Map.Entry<String, List<Post>> entry : grouped.entrySet()) {
            String category = entry.getKey();
            List<Post> categoryPosts = entry.getValue();

            // 只对有足够讨论量或风险较高的类别创建事件
            if (categoryPosts.size() < 2) continue;

            // 按风险排序，取最高风险的帖子作为事件核心
            categoryPosts.sort((a, b) -> b.getRiskScore() - a.getRiskScore());

            // 计算事件聚合评分
            int totalInteractions = categoryPosts.stream()
                .mapToInt(p -> p.getCommentCount() + p.getLikeCount())
                .sum();
            int totalViews = categoryPosts.stream()
                .mapToInt(Post::getViewCount)
                .sum();
            long negCount = categoryPosts.stream()
                .filter(p -> "负面".equals(p.getEmotion()))
                .count();
            double negRatio = (double) negCount / categoryPosts.size();

            int eventScore = 0;
            if (categoryPosts.size() > 10) eventScore += 25;
            else if (categoryPosts.size() > 5) eventScore += 15;
            else eventScore += 8;

            if (totalInteractions > 50) eventScore += 20;
            else if (totalInteractions > 20) eventScore += 12;

            if (totalViews > 2000) eventScore += 15;
            else if (totalViews > 500) eventScore += 8;

            if (negRatio > 0.5) eventScore += 20;
            else if (negRatio > 0.3) eventScore += 10;

            if (category.contains("诈骗") || category.contains("消防") || category.contains("突发") || category.contains("治安")) {
                eventScore += 20;
            } else if (category.contains("交通")) {
                eventScore += 10;
            }

            eventScore = Math.min(eventScore, 100);

            // 只创建有意义的事件
            if (eventScore < 30) continue;

            String id = String.valueOf(eventId++);
            EventEntity event = new EventEntity();
            event.setId(id);
            event.setTitle(generateEventTitle(category, categoryPosts));
            event.setCategory(category);
            event.setRiskScore(eventScore);
            event.setRisk(scoreToLevel(eventScore));
            event.setPostCount(categoryPosts.size());
            event.setAffectedRange(generateAffectedRange(totalViews));
            event.setUrgency(eventScore >= 70 ? "紧急" : eventScore >= 40 ? "关注" : "一般");
            event.setEmotionSummary(String.format("负面占比%.0f%%", negRatio * 100));
            event.setSummary(generateEventSummary(category, categoryPosts, eventScore));
            event.setCreatedAt(LocalDateTime.now());
            event.setUpdatedAt(LocalDateTime.now());

            // 默认状态
            if (eventScore >= 70) {
                event.setStatus("待研判");
            } else {
                event.setStatus("已确认");
            }

            eventRepository.save(event);

            // 关联帖子
            for (Post p : categoryPosts) {
                p.setEventId(id);
                postRepository.save(p);
            }
        }

        log.info("事件聚合完成，共生成 {} 个事件", eventRepository.count());
    }

    private String generateEventTitle(String category, List<Post> posts) {
        // 取热度最高帖子的标题作为事件标题
        Post top = posts.get(0);
        String title = top.getTitle();
        if (title != null && title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        return title != null ? title : category + "相关讨论";
    }

    private String generateAffectedRange(int views) {
        if (views > 2000) return "广泛（" + views + "+人关注）";
        if (views > 500) return "较广（" + views + "+人关注）";
        if (views > 100) return "一般（" + views + "+人关注）";
        return "较小（" + views + "+人关注）";
    }

    private String generateEventSummary(String category, List<Post> posts, int score) {
        Post top = posts.get(0);
        String summary = String.format("该事件属于【%s】类别，共聚合 %d 条相关讨论。", category, posts.size());
        if (top.getContent() != null && top.getContent().length() > 0) {
            String excerpt = top.getContent().length() > 60 ? top.getContent().substring(0, 60) + "..." : top.getContent();
            summary += "典型内容：" + excerpt;
        }
        summary += String.format(" 综合风险评分 %d 分，属于%s风险事件。", score, scoreToLevel(score));
        return summary;
    }

    /**
     * 获取风险判断依据
     */
    public List<Map<String, Object>> getRiskReasons(EventEntity event, List<Post> posts) {
        List<Map<String, Object>> reasons = new ArrayList<>();

        // 讨论量
        int totalPosts = posts.size();
        if (totalPosts > 10) {
            reasons.add(reason("讨论量较大", totalPosts + "条相关讨论", "+25分"));
        } else if (totalPosts > 5) {
            reasons.add(reason("讨论量中等", totalPosts + "条相关讨论", "+15分"));
        } else {
            reasons.add(reason("讨论量较少", totalPosts + "条相关讨论", "+8分"));
        }

        // 负面情绪
        long negCount = posts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
        double negRatio = posts.isEmpty() ? 0 : (double) negCount / posts.size();
        if (negRatio > 0.5) {
            reasons.add(reason("负面情绪占比较高", String.format("负面占比%.0f%%", negRatio * 100), "+20分"));
        } else if (negRatio > 0.3) {
            reasons.add(reason("负面情绪明显", String.format("负面占比%.0f%%", negRatio * 100), "+10分"));
        }

        // 事件类型
        String cat = event.getCategory();
        if (cat != null && (cat.contains("诈骗") || cat.contains("消防") || cat.contains("突发") || cat.contains("治安"))) {
            reasons.add(reason("属于高风险类型", cat, "+20分"));
        } else if (cat != null && cat.contains("交通")) {
            reasons.add(reason("属于中风险类型", cat, "+10分"));
        }

        // 影响范围
        int totalViews = posts.stream().mapToInt(Post::getViewCount).sum();
        if (totalViews > 2000) {
            reasons.add(reason("影响范围广", totalViews + "+人关注", "+15分"));
        } else if (totalViews > 500) {
            reasons.add(reason("影响范围较广", totalViews + "+人关注", "+8分"));
        }

        return reasons;
    }

    private Map<String, Object> reason(String reason, String detail, String score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reason", reason);
        m.put("detail", detail);
        m.put("score", score);
        return m;
    }

    /**
     * 获取近N天的日期标签
     */
    public List<String> getLastNDays(int n) {
        List<String> days = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("M/d");
        for (int i = n - 1; i >= 0; i--) {
            days.add(now.minusDays(i).format(fmt));
        }
        return days;
    }
}
