package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 任务二专用结构化模型客户端；沿用平台的 OpenAI-compatible 配置。 */
@Service
public class Task2StructuredLlmClient {
    private final RestClient.Builder builder;
    private final ObjectMapper mapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final int maxTokens;

    public Task2StructuredLlmClient(
        RestClient.Builder builder,
        ObjectMapper mapper,
        @Value("${yuqing.task2.api-key:${yuqing.ai.api-key:}}") String apiKey,
        @Value("${yuqing.task2.base-url:${yuqing.ai.base-url:}}") String baseUrl,
        @Value("${yuqing.task2.model:${yuqing.ai.model:}}") String model,
        @Value("${yuqing.task2.timeout-seconds:120}") int timeoutSeconds,
        @Value("${yuqing.task2.max-tokens:6000}") int maxTokens
    ) {
        this.builder = builder;
        this.mapper = mapper;
        this.apiKey = text(apiKey);
        this.baseUrl = trimSlash(text(baseUrl));
        this.model = text(model);
        this.timeoutSeconds = Math.max(10, timeoutSeconds);
        this.maxTokens = Math.max(500, maxTokens);
    }

    public boolean enabled() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    public String modelName() {
        return enabled() ? model : "未配置";
    }

    public JsonNode generate(String systemPrompt, Object input) {
        if (!enabled()) throw new IllegalStateException("任务二模型未配置");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", mapper.writeValueAsString(input))
            ));
            payload.put("temperature", 0);
            payload.put("max_tokens", maxTokens);
            payload.put("enable_thinking", false);

            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
            String raw = builder.baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(factory).build().post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(String.class);
            String content = mapper.readTree(raw).path("choices").path(0).path("message").path("content").asText();
            return mapper.readTree(stripFence(content));
        } catch (Exception ex) {
            throw new IllegalStateException("任务二模型调用或JSON校验失败：" + ex.getMessage(), ex);
        }
    }

    private static String stripFence(String value) {
        String result = text(value);
        if (result.startsWith("```")) {
            int firstLine = result.indexOf('\n');
            int lastFence = result.lastIndexOf("```");
            if (firstLine >= 0 && lastFence > firstLine) result = result.substring(firstLine + 1, lastFence).trim();
        }
        return result;
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
