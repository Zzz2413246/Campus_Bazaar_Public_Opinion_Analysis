package com.nankai.yuqing.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 《分类.docx》确定的校园安全最终分类标准。 */
public final class SafetyCategoryStandard {

    public static final Map<String, String> CODE_TO_NAME;
    public static final List<String> CATEGORIES;

    static {
        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("personal_security", "个人安全");
        categories.put("accidental_injury", "意外伤害");
        categories.put("fire_electrical", "消防与电气安全");
        categories.put("facility_hazard", "建筑与设施安全");
        categories.put("public_health", "食品与公共卫生");
        categories.put("traffic", "交通安全");
        categories.put("cyber_security", "网络与数据安全");
        categories.put("property_security", "财产安全");
        categories.put("mental_crisis", "心理危机");
        categories.put("lab_hazard", "实验室安全");
        categories.put("public_order", "公共秩序与活动安全");
        categories.put("environment", "环境安全");
        categories.put("natural_disaster", "自然灾害");
        categories.put("political_security", "政治与国家安全");
        categories.put("hate_discrimination", "仇恨与身份歧视");
        categories.put("campus_rumor_reputation", "校园谣言与声誉风险");
        categories.put("other", "其他校园安全");
        categories.put("undetermined", "疑似主题无法确定");
        CODE_TO_NAME = Map.copyOf(categories);
        CATEGORIES = List.copyOf(categories.values());
    }

    private SafetyCategoryStandard() {}

    public static String fromExternal(String code, String overallLabel) {
        if (code != null && CODE_TO_NAME.containsKey(code)) return CODE_TO_NAME.get(code);
        if ("SAFETY".equalsIgnoreCase(overallLabel)) return CODE_TO_NAME.get("other");
        return CODE_TO_NAME.get("undetermined");
    }
}
