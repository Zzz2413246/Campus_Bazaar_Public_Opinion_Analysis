package com.nankai.yuqing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI-compatible chat-completions client.
 *
 * <p>The assistant always has a local structured-data fallback. This client is
 * activated only when API key, base URL and model are all configured.</p>
 */
@Service
public class AssistantLlmClient {

    private static final Logger log = LoggerFactory.getLogger(AssistantLlmClient.class);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutHintSeconds;
    private final boolean enableThinking;
    private final int maxTokens;

    public AssistantLlmClient(
        RestClient.Builder restClientBuilder,
        ObjectMapper objectMapper,
        @Value("${yuqing.ai.api-key:}") String apiKey,
        @Value("${yuqing.ai.base-url:}") String baseUrl,
        @Value("${yuqing.ai.model:}") String model,
        @Value("${yuqing.ai.timeout-seconds:30}") int timeoutHintSeconds,
        @Value("${yuqing.ai.enable-thinking:false}") boolean enableThinking,
        @Value("${yuqing.ai.max-tokens:600}") int maxTokens
    ) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
        this.model = model == null ? "" : model.trim();
        this.timeoutHintSeconds = timeoutHintSeconds;
        this.enableThinking = enableThinking;
        this.maxTokens = maxTokens;
    }

    public boolean isEnabled() {
        return !apiKey.isBlank() && !baseUrl.isBlank() && !model.isBlank();
    }

    public String modelName() {
        return isEnabled() ? model : "本地数据分析引擎";
    }

    public Optional<String> answer(String question,
                                   List<Map<String, String>> history,
                                   String dataContext,
                                   String fallbackAnswer) {
        if (!isEnabled()) return Optional.empty();

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                "role", "system",
                "content", """
                    你是高校安全舆情分析助手。只能依据“平台数据上下文”回答，不得补造事实、数字或事件。
                    回答应先给结论，再给必要的数据依据和可执行建议；用简洁中文 Markdown。
                    普通问题控制在 300 字以内，只有用户明确要求详细分析时才展开。
                    若数据不足，明确说明不足。不要输出个人身份信息。不要声称可以执行系统中不存在的操作。

                    平台数据上下文：
                    """ + dataContext
            ));
            for (Map<String, String> item : history.stream().skip(Math.max(0, history.size() - 8)).toList()) {
                String role = item.getOrDefault("role", "");
                String content = item.getOrDefault("content", "");
                if (("user".equals(role) || "assistant".equals(role)) && !content.isBlank()) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
            messages.add(Map.of(
                "role", "user",
                "content", question + "\n\n本地分析引擎给出的参考结果：\n" + fallbackAnswer
            ));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", messages);
            payload.put("temperature", 0.2);
            payload.put("max_tokens", Math.max(100, maxTokens));
            // Some Qwen models enable reasoning by default. The assistant favors
            // interactive latency; complex deployments can opt in via configuration.
            payload.put("enable_thinking", enableThinking);

            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
            requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutHintSeconds)));
            String raw = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build()
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception exception) {
            // External AI must never make the structured-data assistant unavailable.
            log.warn("AI enhancement request failed; falling back to structured answer: {}",
                exception.getMessage());
            return Optional.empty();
        }
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
