package com.nankai.yuqing.controller;

import com.nankai.yuqing.model.Post;
import com.nankai.yuqing.repository.PostRepository;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 趋势分析接口
 * 字段命名与前端 Trends.vue 严格对齐：
 *   - 时间轴统一使用 labels
 *   - 数值序列统一使用 data
 *   - 堆叠图使用 series: [{name, data}]
 */
@RestController
@RequestMapping("/api/trends")
public class TrendsController {

    private final PostRepository postRepository;

    public TrendsController(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @GetMapping
    public Map<String, Object> trends(
        @RequestParam(defaultValue = "7") int days,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate
    ) {
        List<Post> posts = postRepository.findAll();
        Map<String, Object> result = new LinkedHashMap<>();

        LocalDate rangeEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate rangeStart;
        if (startDate != null && endDate != null) {
            rangeStart = startDate;
        } else {
            int safeDays = Math.max(1, Math.min(days, 366));
            rangeStart = rangeEnd.minusDays(safeDays - 1L);
        }
        if (rangeStart.isAfter(rangeEnd)) {
            LocalDate temp = rangeStart;
            rangeStart = rangeEnd;
            rangeEnd = temp;
        }
        // 最多返回一年数据，避免过长时间轴影响图表可读性。
        if (ChronoUnit.DAYS.between(rangeStart, rangeEnd) > 365) {
            rangeStart = rangeEnd.minusDays(365);
        }

        final LocalDate finalStart = rangeStart;
        final LocalDate finalEnd = rangeEnd;
        List<Post> rangePosts = posts.stream()
            .filter(p -> p.getPublishTime() != null)
            .filter(p -> {
                LocalDate date = p.getPublishTime().toLocalDate();
                return !date.isBefore(finalStart) && !date.isAfter(finalEnd);
            })
            .toList();
        List<LocalDate> dates = buildDates(rangeStart, rangeEnd);

        result.put("areaData", buildAreaData(rangePosts, dates));
        result.put("emotionData", buildEmotionData(rangePosts, dates));
        result.put("stackData", buildStackData(rangePosts, dates));
        result.put("sourceData", buildSourceData(rangePosts));
        result.put("topItems", buildTopItems(rangePosts));

        Map<String, Object> range = new LinkedHashMap<>();
        range.put("startDate", rangeStart);
        range.put("endDate", rangeEnd);
        range.put("days", dates.size());
        result.put("range", range);

        return result;
    }

    private List<LocalDate> buildDates(LocalDate start, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            dates.add(date);
        }
        return dates;
    }

    private List<String> buildLabels(List<LocalDate> dates) {
        return dates.stream()
            .map(date -> date.getMonthValue() + "/" + date.getDayOfMonth())
            .toList();
    }

    private Map<String, Object> buildAreaData(List<Post> posts, List<LocalDate> dates) {
        List<Integer> values = new ArrayList<>();
        for (LocalDate date : dates) {
            final LocalDate fd = date;
            int count = (int) posts.stream().filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(fd)).count();
            values.add(count);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", buildLabels(dates));   // 对齐前端 ad.labels
        m.put("data", values);   // 对齐前端 ad.data
        return m;
    }

    private Map<String, Object> buildEmotionData(List<Post> posts, List<LocalDate> dates) {
        List<Integer> positive = new ArrayList<>();
        List<Integer> neutral = new ArrayList<>();
        List<Integer> negative = new ArrayList<>();

        for (LocalDate date : dates) {
            final LocalDate fd = date;
            List<Post> dayPosts = posts.stream().filter(p -> p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(fd)).toList();
            int total = dayPosts.size();
            int pos = (int) dayPosts.stream().filter(p -> "正面".equals(p.getEmotion())).count();
            int neu = (int) dayPosts.stream().filter(p -> "中性".equals(p.getEmotion())).count();
            int neg = (int) dayPosts.stream().filter(p -> "负面".equals(p.getEmotion())).count();
            // 无数据天显示0%，避免 0/1 的误导
            if (total == 0) {
                positive.add(0);
                neutral.add(0);
                negative.add(0);
            } else {
                positive.add(pos * 100 / total);
                neutral.add(neu * 100 / total);
                negative.add(neg * 100 / total);
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", buildLabels(dates));   // 对齐前端 ed.labels
        m.put("positive", positive);
        m.put("neutral", neutral);
        m.put("negative", negative);
        return m;
    }

    private Map<String, Object> buildStackData(List<Post> posts, List<LocalDate> dates) {
        // 类别名与 AnalysisService.SAFETY_KEYWORDS 完全一致
        String[] cats = {"诈骗与财产安全", "消防与用电安全", "宿舍设施问题", "食堂与餐饮问题", "校园交通安全"};

        // 先按类别计算每日序列
        Map<String, List<Integer>> seriesMap = new LinkedHashMap<>();
        for (String cat : cats) {
            List<Integer> series = new ArrayList<>();
            for (LocalDate date : dates) {
                final LocalDate fd = date;
                int count = (int) posts.stream()
                    .filter(p -> cat.equals(p.getSafetyCategory()) && p.getPublishTime() != null && p.getPublishTime().toLocalDate().equals(fd))
                    .count();
                series.add(count);
            }
            seriesMap.put(cat, series);
        }

        // 转为前端期望的 series: [{name, data}] 格式
        List<Map<String, Object>> seriesList = new ArrayList<>();
        for (String cat : cats) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", cat);
            s.put("data", seriesMap.get(cat));
            seriesList.add(s);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", buildLabels(dates));      // 对齐前端 sd.labels
        m.put("categories", Arrays.asList(cats));
        m.put("series", seriesList);              // 对齐前端 sd.series
        return m;
    }

    private Map<String, Object> buildSourceData(List<Post> posts) {
        Map<String, Integer> catCount = new LinkedHashMap<>();
        for (Post p : posts) {
            String cat = p.getSafetyCategory() != null ? p.getSafetyCategory() : "其他";
            catCount.merge(cat, 1, Integer::sum);
        }

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        catCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> {
                labels.add(e.getKey());
                values.add(e.getValue());
            });

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labels", labels);
        m.put("values", values);
        return m;
    }

    /**
     * 安全议题分布 TOP5
     * pct = 该类别帖子数 占 所有安全相关帖子总数 的百分比（真实占比，非增长率）
     */
    private List<Map<String, Object>> buildTopItems(List<Post> posts) {
        // 全部安全相关帖子（safetyCategory 非空）
        List<Post> safetyPosts = posts.stream()
            .filter(p -> p.getSafetyCategory() != null)
            .toList();

        // 按安全类别统计
        Map<String, Long> catCount = new LinkedHashMap<>();
        for (Post p : safetyPosts) {
            catCount.merge(p.getSafetyCategory(), 1L, Long::sum);
        }

        // 按数量降序取 TOP5
        List<Map.Entry<String, Long>> sorted = catCount.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .toList();

        // 占比基数：所有安全相关帖子总数（而非仅 TOP5 求和）
        long totalSafety = safetyPosts.size();

        String[] colors = {"from-rose-500 to-rose-400", "from-amber-500 to-amber-400", "from-amber-500 to-amber-400", "from-brand-500 to-brand-400", "from-brand-500 to-brand-400"};

        List<Map<String, Object>> result = new ArrayList<>();
        int max = sorted.isEmpty() ? 1 : sorted.get(0).getValue().intValue();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Long> e = sorted.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", e.getKey());
            // 真实占比：该类别 / 所有安全帖子总数
            m.put("pct", totalSafety > 0 ? (int) (e.getValue() * 100 / totalSafety) : 0);
            m.put("count", e.getValue());
            // 进度条宽度：按 TOP5 内最大值归一化到 10~95
            m.put("w", max > 0 ? Math.max(10, e.getValue().intValue() * 95 / max) : 10);
            m.put("rank", i + 1);
            m.put("c", colors[i % colors.length]);
            result.add(m);
        }
        return result;
    }
}
