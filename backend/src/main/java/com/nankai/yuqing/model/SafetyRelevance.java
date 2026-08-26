package com.nankai.yuqing.model;

import java.util.Locale;
import java.util.Set;

/** 安全舆情三分类的唯一标准字段取值。 */
public final class SafetyRelevance {

    public static final String RELATED = "related";
    public static final String UNRELATED = "unrelated";
    public static final String UNCERTAIN = "uncertain";
    public static final Set<String> VALUES = Set.of(RELATED, UNRELATED, UNCERTAIN);

    private SafetyRelevance() {}

    /** 兼容历史分类文件，但系统内部及 API 只返回新值。 */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return UNCERTAIN;
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "related", "safety" -> RELATED;
            case "unrelated", "non_safety", "non-safety" -> UNRELATED;
            case "uncertain" -> UNCERTAIN;
            default -> UNCERTAIN;
        };
    }

    public static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : normalize(value);
    }

    public static boolean isRelated(String value) {
        return RELATED.equals(normalize(value));
    }

    public static boolean isUnrelated(String value) {
        return UNRELATED.equals(normalize(value));
    }
}
