package com.nankai.yuqing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nankai.yuqing.model.SystemSetting;
import com.nankai.yuqing.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 安全议题分类与自定义规则的持久化、校验和运行时快照。 */
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

    public record Snapshot(List<String> categories,
                           Map<String, List<String>> categoryRules) {
        public boolean categoryEnabled(String category) {
            return categories.contains(category);
        }

        public static Snapshot defaults() {
            return new Snapshot(List.copyOf(DEFAULT_CATEGORIES), Map.of());
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
        return result;
    }

    @Transactional
    public Map<String, Object> update(Map<String, Object> body) {
        Snapshot current = getSnapshot();
        List<String> categories = normalizeCategories(body.get("categories"), current.categories());
        Map<String, List<String>> rules = normalizeRules(body.get("categoryRules"), categories, current.categoryRules());
        Snapshot next = new Snapshot(List.copyOf(categories), immutableRules(rules));

        try {
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("categories", categories);
            stored.put("categoryRules", rules);
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
            return new Snapshot(List.copyOf(categories), immutableRules(rules));
        } catch (Exception ignored) {
            return Snapshot.defaults();
        }
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
