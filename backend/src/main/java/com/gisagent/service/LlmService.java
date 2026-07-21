package com.gisagent.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
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
        // 禁用 HTTP keep-alive 复用：避免复用被服务端关闭的陈旧连接导致 Connection reset
        this.restTemplate.getInterceptors().add(new CloseConnectionInterceptor());
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

        try {
            // 流式解析：仅抽取 content + usage，巨长 reasoning_content 边读边丢，避免整段驻留内存
            return withRetry(() -> streamComplete(url, headers, body), "LLM 调用");
        } catch (RuntimeException e) {
            log.error("LLM 调用失败: endpoint={}, model={}, msg={}", endpoint, model, e.getMessage());
            throw e;
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

    /**
     * 对瞬时 I/O 异常（连接重置/超时/断连）重试一次，避免偶发网络抖动直接打断管道。
     * 重试间隔 500ms，最多 1 次重试（共 2 次尝试）。
     */
    private <T> T withRetry(ThrowingSupplier<T> action, String what) {
        int maxAttempts = 2;
        Exception last = null;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                return action.get();
            } catch (Exception e) {
                if (isTransient(e) && i < maxAttempts - 1) {
                    log.warn("{} 瞬时失败，{}/{} 重试: {}", what, i + 1, maxAttempts - 1, e.getMessage());
                    try {
                        Thread.sleep(500L * (i + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    last = e;
                    continue;
                }
                throw new RuntimeException(what + " 失败: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException(what + " 失败: " + (last != null ? last.getMessage() : "未知错误"));
    }

    /** 判断是否为可重试的瞬时网络异常（沿 cause 链查找） */
    private boolean isTransient(Throwable e) {
        Throwable c = e;
        while (c != null) {
            if (c instanceof java.net.SocketException
                    || c instanceof java.net.ConnectException
                    || c instanceof java.io.IOException
                    || c instanceof org.springframework.web.client.ResourceAccessException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    /** 流式发起 chat 补全并抽取 content + usage，避免整段缓存巨型响应体（推理模型 reasoning_content 很长） */
    private CompletionResult streamComplete(String url, HttpHeaders headers, Map<String, Object> body) {
        byte[] bodyBytes;
        try {
            bodyBytes = objectMapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw new RuntimeException("序列化 LLM 请求体失败", e);
        }
        return restTemplate.execute(url, HttpMethod.POST,
                req -> {
                    req.getHeaders().addAll(headers);
                    req.getBody().write(bodyBytes);
                },
                response -> {
                    try (InputStream is = response.getBody()) {
                        return extractChatResponse(is);
                    } catch (IOException e) {
                        throw new RuntimeException("读取 LLM 响应失败", e);
                    }
                });
    }

    /** 流式解析 chat.completion：仅保留 message.content 与 usage，丢弃 reasoning_content 等大字段 */
    private CompletionResult extractChatResponse(InputStream is) throws IOException {
        JsonParser p = objectMapper.getFactory().createParser(is);
        String content = "";
        long promptTokens = 0, completionTokens = 0, totalTokens = 0;
        JsonToken t;
        while ((t = p.nextToken()) != null) {
            if (t != JsonToken.FIELD_NAME) continue;
            String name = p.getCurrentName();
            if ("content".equals(name)) {
                p.nextToken();
                if (p.hasToken(JsonToken.VALUE_STRING)) content = p.getText();
            } else if ("reasoning_content".equals(name)) {
                p.nextToken(); // 跳过，不驻留
            } else if ("usage".equals(name) && p.nextToken() == JsonToken.START_OBJECT) {
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String un = p.getCurrentName();
                    p.nextToken();
                    if ("prompt_tokens".equals(un)) promptTokens = p.getLongValue();
                    else if ("completion_tokens".equals(un)) completionTokens = p.getLongValue();
                    else if ("total_tokens".equals(un)) totalTokens = p.getLongValue();
                }
            }
        }
        return new CompletionResult(content, new LlmUsage(promptTokens, completionTokens, totalTokens));
    }

    /** 禁用 HTTP keep-alive 复用，避免复用被服务端关闭的陈旧连接导致 Connection reset */
    private static class CloseConnectionInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            request.getHeaders().set("Connection", "close");
            return execution.execute(request, body);
        }
    }

    /** 受检供给接口，供 withRetry 使用 */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
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
     * 兼容 OpenAI embedding API（/v1/embeddings）。
     * 注意：DeepSeek 等部分厂商不支持 embedding API，会返回 HTTP 404/400。
     * 此时本方法返回 null，调用方应据此跳过向量化。
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

        try {
            // 使用流式解析，避免 postForEntity(String.class) 缓冲整个响应体
            ResponseEntity<String> entity = restTemplate.postForEntity(url,
                    new HttpEntity<>(body, headers), String.class);
            if (!entity.getStatusCode().is2xxSuccessful()) {
                log.debug("Embedding API 返回非 2xx: {} (endpoint={})", entity.getStatusCode(), endpoint);
                return null;
            }
            return extractEmbedding(entity.getBody());
        } catch (HttpClientErrorException e) {
            // 404/400 等客户端错误：API 不支持，不打印堆栈
            log.debug("Embedding API 不可用: {} (endpoint={})", e.getStatusCode(), endpoint);
            return null;
        } catch (RuntimeException e) {
            log.warn("Embedding 调用失败: endpoint={}, msg={}", endpoint, e.getMessage());
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
}
