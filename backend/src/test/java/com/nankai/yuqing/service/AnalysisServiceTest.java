package com.nankai.yuqing.service;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.model.PostComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTest {

    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(null, null);
    }

    @Test
    void ordinarySecondHandListingIsNotFraudOrTrafficRisk() {
        Post post = post("八里台毕业出自行车", "公路自行车正常使用，现在450元出，可议价", "二手闲置");

        service.analyzePost(post);

        assertNull(post.getSafetyCategory());
        assertEquals(0, post.getRiskScore());
        assertEquals("低", post.getRiskLevel());
    }

    @Test
    void explicitFraudEvidenceIsDetectedEvenInSecondHandSource() {
        Post post = post("二手交易被骗", "转账后失联，对方收钱不发货，提醒大家避雷", "二手闲置");

        service.analyzePost(post);

        assertEquals("诈骗与财产安全", post.getSafetyCategory());
        assertEquals("二手交易诈骗", post.getTopic());
        assertTrue(post.getClassificationConfidence() >= 70);
        assertTrue(post.getRiskScore() >= 40);
    }

    @Test
    void dormFaultNeedsProblemContextAndGetsSpecificTopic() {
        Post post = post("宿舍空调坏了", "宿舍空调故障，已经报修三天还没解决", "打听求助");

        service.analyzePost(post);

        assertEquals("宿舍设施问题", post.getSafetyCategory());
        assertEquals("空调与热水", post.getTopic());
    }

    @Test
    void electricVehicleFirePrefersFireCategoryOverTraffic() {
        Post post = post("电动车充电时起火", "宿舍楼下电动车冒烟，现场已报警", "打听求助");

        service.analyzePost(post);

        assertEquals("消防与用电安全", post.getSafetyCategory());
        assertEquals("电动车充电", post.getTopic());
        assertEquals("高", post.getRiskLevel());
    }

    @Test
    void resolvedThanksIsPositiveEmotion() {
        Post post = post("问题已解决", "非常感谢大家帮忙，事情已经解决了，谢谢", "打听求助");

        service.analyzePost(post);

        assertEquals("正面", post.getEmotion());
    }

    @Test
    void jobHoppingSlangIsNotFraud() {
        Post post = post("实习求问", "这家公司工作太少，这种情况要跑路吗", "打听求助");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void colloquialHelpExpressionIsNotEmergency() {
        Post post = post("谁能借我卫生纸", "忘带纸了求救命，正在图书馆挣扎", "打听求助");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void figurativeRobberyInDatingPostIsNotSecurityIncident() {
        Post post = post("匹配的人有点高冷", "想要入室抢劫的爱情，但对方不主动聊天", "恋爱交友");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void secondHandBoilerplateScammerWarningIsNotIncident() {
        Post post = post("收往年课件", "5元收资料，骗子别来，谢谢", "二手闲置");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void examExplosionMetaphorIsNotEmergency() {
        Post post = post("期末考爆炸了", "两门专业课没考好，绩点要下滑了", "打听求助");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void gameProductionExplosionIsNotEmergency() {
        Post post = post("游戏更新消息", "下个版本产能爆炸，剧情迎来大爆点", "恋爱交友");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void contactingTeacherIsNotPersonalSafetyHarassment() {
        Post post = post("不要私发骚扰老师要求捞了", "不要因为成绩去私发消息打扰老师", "打听求助");
        service.analyzePost(post);
        assertNull(post.getSafetyCategory());
    }

    @Test
    void singleTangentialCommentCannotChangeOriginalClassification() {
        Post post = post("普通求助", "大家帮忙看看这个问题怎么解决", "打听求助");

        service.analyzePost(post, List.of(comment("有人说像诈骗，大家小心")));

        assertNull(post.getSafetyCategory());
        assertEquals(0, post.getRiskScore());
        assertEquals("原帖文本", post.getAnalysisBasis());
        assertEquals(1, post.getAnalyzedCommentCount());
    }

    @Test
    void consistentCommentEvidenceCreatesReviewSuggestionWithoutOverwritingCategory() {
        Post post = post("交易求助", "想问问大家这种情况怎么办", "打听求助");

        service.analyzePost(post, List.of(
            comment("我也被骗了，对方转账后失联"),
            comment("确认是诈骗，收钱不发货，太坑人了")
        ));

        assertNull(post.getSafetyCategory());
        assertEquals("诈骗与财产安全", post.getCommentSuggestedCategory());
        assertEquals(2, post.getCommentSuggestionCount());
        assertEquals("原帖文本", post.getAnalysisBasis());
        assertEquals(0, post.getCommentRiskAdjustment());
    }

    @Test
    void repeatedTeacherCommunicationAccusationsAreNotPersonalSafetyConsensus() {
        Post post = post("课程成绩讨论", "想请教老师评分标准", "打听求助");

        service.analyzePost(post, List.of(
            comment("反复发消息是在骚扰老师"),
            comment("不要三番五次骚扰老师要求回复"),
            comment("这种私发消息打扰老师的方式不合适")
        ));

        assertNull(post.getSafetyCategory());
        assertEquals(0, post.getCommentRiskAdjustment());
    }

    @Test
    void matchingNegativeCommentsIncreaseRiskWithinTwelvePointLimit() {
        Post baseline = post("二手交易被骗", "转账后失联，对方收钱不发货", "打听求助");
        Post assisted = post("二手交易被骗", "转账后失联，对方收钱不发货", "打听求助");
        service.analyzePost(baseline);
        service.analyzePost(assisted, List.of(
            comment("我也被骗了，真的非常生气"),
            comment("确认是诈骗，太坑人了"),
            comment("大家小心，对方收钱不发货")
        ));

        assertTrue(assisted.getRiskScore() > baseline.getRiskScore());
        assertTrue(assisted.getRiskScore() - baseline.getRiskScore() <= 12);
        assertEquals("原帖文本+评论佐证", assisted.getAnalysisBasis());
    }

    private Post post(String title, String content, String source) {
        Post post = new Post();
        post.setId(title);
        post.setTitle(title);
        post.setContent(content);
        post.setCategoryName(source);
        post.setCommentCount(0);
        post.setLikeCount(0);
        post.setViewCount(10);
        return post;
    }

    private PostComment comment(String content) {
        PostComment comment = new PostComment();
        comment.setId(content);
        comment.setThreadId("test-thread");
        comment.setContent(content);
        comment.setLikeCount(0);
        comment.setDislikeCount(0);
        comment.setIsAuthor(0);
        return comment;
    }
}
