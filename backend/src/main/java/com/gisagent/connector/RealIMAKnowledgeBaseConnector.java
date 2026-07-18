package com.gisagent.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IMA 知识库连接器真实实现。
 * 调用 IMA 开放接口（笔记检索）作为方案生成的知识源。
 * 当 ima.mock-enabled=false 时启用，凭证通过环境变量注入（不落库）：
 *   IMA_OPENAPI_CLIENTID / IMA_OPENAPI_APIKEY
 * Base URL 默认 https://ima.qq.com/openapi/note/v1，可由 ima.openapi-base-url 覆盖。
 */
@Component
@ConditionalOnProperty(name = "ima.mock-enabled", havingValue = "false")
public class RealIMAKnowledgeBaseConnector implements IMAKnowledgeBaseConnector {

    private static final Logger log = LoggerFactory.getLogger(RealIMAKnowledgeBaseConnector.class);
    private static final String DEFAULT_BASE = "https://ima.qq.com/openapi/note/v1";

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ima.openapi-base-url:" + DEFAULT_BASE + "}")
    private String baseUrl;

    @Value("${ima.openapi.client-id:}")
    private String clientId;

    @Value("${ima.openapi.api-key:}")
    private String apiKey;

    @Value("${ima.connect-timeout-ms:8000}")
    private int connectTimeoutMs;

    @Value("${ima.read-timeout-ms:15000}")
    private int readTimeoutMs;

    @jakarta.annotation.PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (clientId != null && !clientId.isBlank()) {
            headers.set("ima-openapi-clientid", clientId);
        }
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("ima-openapi-apikey", apiKey);
        }
        return headers;
    }

    @Override
    public boolean testConnection(String kbId, String credential) {
        try {
            String url = baseUrl + "/search_note_book";
            Map<String, Object> body = Map.of(
                    "search_type", 0,
                    "query_info", Map.of("title", "test"),
                    "start", 0, "end", 1);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders());
            restTemplate.postForEntity(url, req, String.class);
            return true;
        } catch (Exception e) {
            log.warn("[IMA] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<SearchResult> search(String kbId, String query, SearchOptions options) {
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn("[IMA] 未配置 Client ID / API Key，跳过真实检索");
            return Collections.emptyList();
        }
        try {
            String url = baseUrl + "/search_note_book";
            Map<String, Object> body = Map.of(
                    "search_type", 0,
                    "query_info", Map.of("title", query == null ? "" : query),
                    "start", 0, "end", Math.max(1, options.topK()));
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders());
            String resp = restTemplate.postForEntity(url, req, String.class).getBody();
            return parseNotes(resp, kbId);
        } catch (Exception e) {
            log.warn("[IMA] 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchResult> parseNotes(String resp, String kbId) {
        List<SearchResult> out = new ArrayList<>();
        if (resp == null || resp.isBlank()) return out;
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.path("data");
            JsonNode notes = data.path("notes");
            if (!notes.isArray()) notes = data.path("list");
            if (!notes.isArray()) notes = data.path("note_list");
            if (!notes.isArray()) return out;
            double score = 0.9;
            for (JsonNode n : notes) {
                String docId = n.path("doc_id").asText(n.path("id").asText(""));
                String title = n.path("title").asText(n.path("name").asText(""));
                String content = n.path("content").asText(n.path("summary").asText(n.path("content_preview").asText("")));
                if (title.isBlank() && content.isBlank()) continue;
                out.add(new SearchResult(
                        docId.isBlank() ? "ima-" + UUID.randomUUID().toString().substring(0, 8) : docId,
                        title, content, score, kbId, Instant.now()));
                score -= 0.05;
            }
        } catch (Exception e) {
            log.warn("[IMA] 解析检索响应失败", e);
        }
        return out;
    }

    @Override
    public KBInfo getKBInfo(String kbId) {
        return new KBInfo(kbId, "IMA 笔记知识源（真实）", "notes", -1, Instant.now());
    }

    @Override
    public List<KBUpdateEvent> getUpdates(String kbId, Instant since) {
        return Collections.emptyList();
    }
}
