package com.gisagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 调用服务。封装 OpenAI 兼容协议，支持不同 Provider / 模型 /
 * Temperature / MaxTokens 配置。
 */
/**
 * 一次补全的返回：文本内容 + token 用量（P7-3 计费需要）。
 * 见独立文件 CompletionResult.java（public 顶层 record 须与文件名一致）。
 */
@Service
@Slf4j
public class LlmService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final int defaultTimeoutSeconds;

    public LlmService(@Value("${llm.default-timeout-seconds:120}") int configuredTimeout) {
        this.defaultTimeoutSeconds = configuredTimeout;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(configuredTimeout));
        this.restTemplate = new RestTemplate(factory);
    }

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
        return completeWithUsage(endpoint, apiKey, model, systemPrompt, userPrompt, temperature, maxTokens).content();
    }

    /**
     * 调用 LLM 进行单次补全，并返回 token 用量（P7-3 计费）。
     * 解析 OpenAI / DeepSeek 兼容响应的 usage 字段：
     * {"usage": {"prompt_tokens": N, "completion_tokens": N, "total_tokens": N}}
     *
     * @return 补全文本 + 用量（解析失败或缺失时 usage 为 ZERO，不影响主流程）
     */
    public CompletionResult completeWithUsage(String endpoint, String apiKey, String model,
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

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            String respBody = response.getBody();
            return new CompletionResult(extractContent(respBody), extractUsage(respBody));
        } catch (Exception e) {
            log.error("LLM 调用失败: endpoint={}, model={}", endpoint, model, e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /** 从响应体解析 usage（缺失/异常时返回 ZERO） */
    private LlmUsage extractUsage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return LlmUsage.ZERO;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode usage = root.path("usage");
            if (usage.isMissingNode() || usage.isNull()) {
                return LlmUsage.ZERO;
            }
            return new LlmUsage(
                    usage.path("prompt_tokens").asLong(0),
                    usage.path("completion_tokens").asLong(0),
                    usage.path("total_tokens").asLong(0));
        } catch (Exception e) {
            log.warn("解析 LLM usage 失败，按 0 计", e);
            return LlmUsage.ZERO;
        }
    }

    /**
     * 连通性测试：发送最小 chat 请求，校验端点/凭证/model 是否有效。
     * 用于 Provider 配置页的「连接测试」按钮，替代恒真占位逻辑。
     * 判定标准：HTTP 2xx 且响应含非空 choices 数组。
     * 注意推理模型(如 deepseek-v4-pro / reasoner)可能 content 为空、答案在 reasoning_content，
     * 因此以 choices 是否存在为准，而非 content 是否非空。
     *
     * @return 端点/凭证/model 有效则为 true
     */
    public boolean testConnect(String endpoint, String apiKey, String model) {
        String url = ensureChatCompletionsUrl(endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", "ping"));
        Map<String, Object> body = new HashMap<>();
        body.put("model", (model != null && !model.isBlank()) ? model : "deepseek-chat");
        body.put("messages", messages);
        body.put("max_tokens", 5);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("LLM 连通性测试返回非 2xx: {}", response.getStatusCode());
                return false;
            }
            // 2xx 且响应含合法 choices 即视为凭证/端点有效。
            // 推理模型(如 deepseek-v4-pro / reasoner)可能 content 为空、答案落在 reasoning_content，
            // 因此不能用 content 是否非空来判断连通性。
            boolean ok = responseHasChoices(response.getBody());
            if (!ok) {
                log.warn("LLM 连通性测试：响应缺少有效的 choices 数组（凭证/端点/model 可能无效）");
            }
            return ok;
        } catch (Exception e) {
            log.warn("LLM 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }

    /** 判断响应是否为合法的 chat.completion（含非空 choices 数组）。 */
    private boolean responseHasChoices(String body) {
        if (body == null || body.isBlank()) return false;
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            return choices.isArray() && choices.size() > 0;
        } catch (Exception e) {
            log.warn("解析 LLM 测试响应失败", e);
            return false;
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

    /**
     * 调用 embedding 模型，返回浮点数向量（1536 维）。
     * 兼容 OpenAI / DeepSeek embedding API（/v1/embeddings）。
     *
     * @param endpoint API 地址
     * @param apiKey   API Key
     * @param text     待向量化的文本
     * @return float[] 向量，失败时返回 null
     */
    public float[] embedding(String endpoint, String apiKey, String text) {
        String url = ensureEmbeddingsUrl(endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", "text-embedding-ada-002"); // OpenAI compatible
        body.put("input", text);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return extractEmbedding(response.getBody());
        } catch (Exception e) {
            log.error("Embedding 调用失败: endpoint={}", endpoint, e);
            return null;
        }
    }

    private String ensureEmbeddingsUrl(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("LLM endpoint 不能为空");
        }
        String base = endpoint.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/embeddings")) {
            return base;
        }
        return base + "/embeddings";
    }

    private float[] extractEmbedding(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                JsonNode emb = data.get(0).path("embedding");
                if (emb.isArray()) {
                    float[] vec = new float[emb.size()];
                    for (int i = 0; i < emb.size(); i++) {
                        vec[i] = emb.get(i).floatValue();
                    }
                    return vec;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("解析 Embedding 响应失败", e);
            return null;
        }
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
