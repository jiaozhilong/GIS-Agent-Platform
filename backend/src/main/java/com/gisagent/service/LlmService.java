package com.gisagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用服务。封装 OpenAI 兼容协议，支持不同 Provider / 模型 /
 * Temperature / MaxTokens 配置。
 */
@Service
@Slf4j
public class LlmService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${llm.default-timeout-seconds:120}")
    private int defaultTimeoutSeconds;

    /**
     * 调用 LLM 进行单次补全。
     *
     * @param endpoint     API 地址（如 https://api.openai.com/v1）
     * @param apiKey       API Key（可为空，本地模型不需要）
     * @param model        模型名（如 gpt-4o）
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param temperature  温度
     * @param maxTokens    最大 token
     * @return LLM 返回的文本
     */
    public String complete(String endpoint, String apiKey, String model,
                            String systemPrompt, String userPrompt,
                            Double temperature, Integer maxTokens) {
        String url = ensureChatCompletionsUrl(endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature != null ? temperature : 0.3);
        body.put("max_tokens", maxTokens != null ? maxTokens : 2048);

        // 结构化输出（如支持）
        Map<String, Object> responseFormat = new HashMap<>();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return extractContent(response.getBody());
        } catch (Exception e) {
            log.error("LLM 调用失败: endpoint={}, model={}", endpoint, model, e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    private String ensureChatCompletionsUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("LLM endpoint 不能为空");
        }
        String base = endpoint.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                return choices.get(0).path("message").path("content").asText("");
            }
            return "";
        } catch (Exception e) {
            log.warn("解析 LLM 响应失败，返回原始文本", e);
            return responseBody;
        }
    }
}
