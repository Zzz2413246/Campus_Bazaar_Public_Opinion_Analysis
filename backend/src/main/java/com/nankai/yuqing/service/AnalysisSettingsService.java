package com.nankai.yuqing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.SystemSetting;
import com.nankai.yuqing.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 安全议题分类、自定义规则与预警规则的持久化、校验和运行时快照。 */
@Service
public class AnalysisSettingsService {

    private static final String SETTINGS_KEY = "analysis.settings";
    public static final List<String> BUILTIN_CATEGORIES = List.of(
        "诈骗与财产安全", "治安与人身安全", "消防与用电安全", "校园交通安全",
        "宿舍设施问题", "食堂与餐饮问题", "突发事件"
    );
    private static final List<String> DEFAULT_CATEGORIES = List.of(
        "诈骗与财产安全", "治安与人身安全", "消防与用电安全", "校园交通安全",
        "宿舍设施问题", "食堂与餐饮问题", "突发事件", "其他"
    );

    public record AlertRules(
        int minPostCount,
        int negativeRatioPercent,
        int minInteractions,
        int minViews,
        int burstWindowHours,
        int burstPostCount,
        int repeatedLocationPostCount,
        List<String> urgentKeywords
    ) {
        public static AlertRules defaults() {
            return new AlertRules(
                4, 35, 50, 5000, 2, 4, 3,
                List.of("聚集", "线下行动", "报警", "起火", "爆炸", "持刀",
                    "跳楼", "轻生", "食物中毒", "救护车")
            );
        }
    }

    public record Snapshot(List<String> categories,
                           Map<String, List<String>> categoryRules,
                           AlertRules alertRules) {
        public boolean categoryEnabled(String category) {
            return categories.contains(category);
        }

        public static Snapshot defaults() {
            return new Snapshot(
                List.copyOf(DEFAULT_CATEGORIES), Map.of(), AlertRules.defaults());
        }
    }

    private final SystemSettingRepository repository;
    private final ObjectMapper objectMapper;
    private volatile Snapshot cache;

    public AnalysisSettingsService(SystemSettingRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Snapshot getSnapshot() {
        Snapshot current = cache;
        if (current != null) return current;
        synchronized (this) {
            if (cache == null) cache = loadSnapshot();
            return cache;
        }
    }

    public Map<String, Object> getSettings() {
        Snapshot snapshot = getSnapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", snapshot.categories());
        result.put("categoryRules", snapshot.categoryRules());
        result.put("builtinCategories", BUILTIN_CATEGORIES);
        result.put("alertRules", alertRulesMap(snapshot.alertRules()));
        return result;
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        Snapshot current = getSnapshot();
        List<String> categories = normalizeCategories(body.get("categories"), current.categories());
        Map<String, List<String>> rules = normalizeRules(body.get("categoryRules"), categories, current.categoryRules());
        AlertRules alertRules = normalizeAlertRules(body.get("alertRules"), current.alertRules());
        Snapshot next = new Snapshot(
            List.copyOf(categories), immutableRules(rules), alertRules);

        try {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("categories", categories);
            stored.put("categoryRules", rules);
            stored.put("alertRules", alertRulesMap(alertRules));
            String json = objectMapper.writeValueAsString(stored);
            SystemSetting entity = repository.findById(SETTINGS_KEY)
                .orElseGet(() -> new SystemSetting(SETTINGS_KEY, json));
            entity.setValue(json);
            entity.setUpdatedAt(LocalDateTime.now());
            repository.save(entity);
            cache = next;
            return getSettings();
        } catch (Exception e) {
            throw new IllegalStateException("系统设置保存失败", e);
        }
    }

    private Snapshot loadSnapshot() {
        Optional<SystemSetting> stored = repository.findById(SETTINGS_KEY);
        if (stored.isEmpty()) return Snapshot.defaults();
        try {
            Map<String, Object> value = objectMapper.readValue(stored.get().getValue(), new TypeReference<>() {});
            List<String> categories = normalizeCategories(value.get("categories"), DEFAULT_CATEGORIES);
            Map<String, List<String>> rules = normalizeRules(value.get("categoryRules"), categories, Map.of());
            AlertRules alertRules = normalizeAlertRules(
                value.get("alertRules"), AlertRules.defaults());
            return new Snapshot(
                List.copyOf(categories), immutableRules(rules), alertRules);
        } catch (Exception ignored) {
            return Snapshot.defaults();
        }
    }

    private AlertRules normalizeAlertRules(Object raw, AlertRules fallback) {
        if (!(raw instanceof Map<?, ?> source)) return fallback;
        return new AlertRules(
            number(source.get("minPostCount"), fallback.minPostCount(), 1, 1000),
            number(source.get("negativeRatioPercent"), fallback.negativeRatioPercent(), 0, 100),
            number(source.get("minInteractions"), fallback.minInteractions(), 0, 1_000_000),
            number(source.get("minViews"), fallback.minViews(), 0, 10_000_000),
            number(source.get("burstWindowHours"), fallback.burstWindowHours(), 1, 168),
            number(source.get("burstPostCount"), fallback.burstPostCount(), 2, 1000),
            number(source.get("repeatedLocationPostCount"), fallback.repeatedLocationPostCount(), 2, 1000),
            normalizeKeywords(source.get("urgentKeywords"), fallback.urgentKeywords())
        );
    }

    private int number(Object raw, int fallback, int min, int max) {
        int value;
        if (raw instanceof Number number) {
            value = number.intValue();
        } else {
            try {
                value = Integer.parseInt(Objects.toString(raw, ""));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return Math.max(min, Math.min(max, value));
    }

    private List<String> normalizeKeywords(Object raw, List<String> fallback) {
        if (raw == null) return List.copyOf(fallback);
        Collection<?> values = raw instanceof Collection<?> collection
            ? collection
            : Arrays.asList(Objects.toString(raw, "").split("[,，]"));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            String keyword = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
            if (keyword.length() >= 2 && keyword.length() <= 30) result.add(keyword);
            if (result.size() >= 50) break;
        }
        return result.isEmpty() ? List.copyOf(fallback) : List.copyOf(result);
    }

    private Map<String, Object> alertRulesMap(AlertRules rules) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("minPostCount", rules.minPostCount());
        result.put("negativeRatioPercent", rules.negativeRatioPercent());
        result.put("minInteractions", rules.minInteractions());
        result.put("minViews", rules.minViews());
        result.put("burstWindowHours", rules.burstWindowHours());
        result.put("burstPostCount", rules.burstPostCount());
        result.put("repeatedLocationPostCount", rules.repeatedLocationPostCount());
        result.put("urgentKeywords", rules.urgentKeywords());
        return result;
    }

    private List<String> normalizeCategories(Object raw, List<String> fallback) {
        if (!(raw instanceof Collection<?> collection)) return List.copyOf(fallback);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object item : collection) {
            String name = Objects.toString(item, "").trim();
            if (!name.isBlank() && name.length() <= 30) result.add(name);
            if (result.size() >= 30) break;
        }
        if (result.isEmpty()) throw new IllegalArgumentException("至少保留一个安全议题分类");
        return new ArrayList<>(result);
    }

    private Map<String, List<String>> normalizeRules(Object raw,
                                                     List<String> categories,
                                                     Map<String, List<String>> fallback) {
        if (!(raw instanceof Map<?, ?> source)) return filterRules(fallback, categories);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String category : categories) {
            Object keywordsRaw = source.get(category);
            if (keywordsRaw == null) continue;
            LinkedHashSet<String> keywords = new LinkedHashSet<>();
            Collection<?> values = keywordsRaw instanceof Collection<?> c
                ? c : Arrays.asList(Objects.toString(keywordsRaw, "").split("[,，]"));
            for (Object value : values) {
                String keyword = Objects.toString(value, "").trim().toLowerCase(Locale.ROOT);
                if (keyword.length() >= 2 && keyword.length() <= 40) keywords.add(keyword);
                if (keywords.size() >= 30) break;
            }
            if (!keywords.isEmpty()) result.put(category, new ArrayList<>(keywords));
        }
        return result;
    }

    private Map<String, List<String>> filterRules(Map<String, List<String>> source, List<String> categories) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String category : categories) {
            List<String> keywords = source.get(category);
            if (keywords != null && !keywords.isEmpty()) result.put(category, List.copyOf(keywords));
        }
        return result;
    }

    private Map<String, List<String>> immutableRules(Map<String, List<String>> rules) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        rules.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(copy);
    }

}
