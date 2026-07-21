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
 * 当 ima.mock-enabled=false 时启用。凭证按用户通过 {@link ImaAuth} 传入（不落全局环境变量、不落库明文）。
 * Base URL 默认 https://ima.qq.com/openapi/note/v1，可由 ImaAuth.baseUrl 或 ima.openapi-base-url 覆盖。
 */
@Component
@ConditionalOnProperty(name = "ima.mock-enabled", havingValue = "false")
public class RealIMAKnowledgeBaseConnector implements IMAKnowledgeBaseConnector {

    private static final Logger log = LoggerFactory.getLogger(RealIMAKnowledgeBaseConnector.class);
    private static final String DEFAULT_BASE = "https://ima.qq.com/openapi/note/v1";

    private RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ima.openapi-base-url:" + DEFAULT_BASE + "}")
    private String fallbackBaseUrl;

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

    private String resolveBaseUrl(ImaAuth auth) {
        if (auth.baseUrl() != null && !auth.baseUrl().isBlank()) return auth.baseUrl().trim();
        return fallbackBaseUrl;
    }

    private HttpHeaders authHeaders(ImaAuth auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (auth.clientId() != null && !auth.clientId().isBlank()) {
            headers.set("ima-openapi-clientid", auth.clientId());
        }
        if (auth.apiKey() != null && !auth.apiKey().isBlank()) {
            headers.set("ima-openapi-apikey", auth.apiKey());
        }
        return headers;
    }

    @Override
    public boolean testConnection(String kbId, ImaAuth auth) {
        if (auth == null || auth.clientId() == null || auth.clientId().isBlank()
                || auth.apiKey() == null || auth.apiKey().isBlank()) {
            log.warn("[IMA] 凭证未配置，跳过连接测试");
            return false;
        }
        try {
            String url = resolveBaseUrl(auth) + "/search_note_book";
            Map<String, Object> body = Map.of(
                    "search_type", 0,
                    "query_info", Map.of("title", "test"),
                    "start", 0, "end", 1);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders(auth));
            restTemplate.postForEntity(url, req, String.class);
            return true;
        } catch (Exception e) {
            log.warn("[IMA] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<SearchResult> search(String kbId, String query, SearchOptions options, ImaAuth auth) {
        if (auth == null || auth.clientId() == null || auth.clientId().isBlank()
                || auth.apiKey() == null || auth.apiKey().isBlank()) {
            log.warn("[IMA] 未配置 Client ID / API Key，跳过真实检索");
            return Collections.emptyList();
        }
        try {
            String url = resolveBaseUrl(auth) + "/search_note_book";
            Map<String, Object> body = Map.of(
                    "search_type", 0,
                    "query_info", Map.of("title", query == null ? "" : query),
                    "start", 0, "end", Math.max(1, options.topK()));
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders(auth));
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
    public List<KBInfo> listKnowledgeBases(ImaAuth auth) {
        if (auth == null || auth.clientId() == null || auth.clientId().isBlank()
                || auth.apiKey() == null || auth.apiKey().isBlank()) {
            log.warn("[IMA] 未配置 Client ID / API Key，跳过拉取知识库列表");
            return Collections.emptyList();
        }
        try {
            String url = resolveBaseUrl(auth) + "/list_note_book";
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(Map.of("start", 0, "end", 200), authHeaders(auth));
            String resp = restTemplate.postForEntity(url, req, String.class).getBody();
            return parseBookList(resp);
        } catch (Exception e) {
            log.warn("[IMA] 拉取知识库列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<KBInfo> parseBookList(String resp) {
        List<KBInfo> out = new ArrayList<>();
        if (resp == null || resp.isBlank()) return out;
        try {
            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.path("data");
            JsonNode list = data.path("note_book_list");
            if (!list.isArray()) list = data.path("book_list");
            if (!list.isArray()) list = data.path("list");
            if (!list.isArray()) list = data.path("note_books");
            if (!list.isArray()) return out;
            for (JsonNode n : list) {
                String kbId = n.path("note_book_id").asText(n.path("kb_id").asText(n.path("id").asText("")));
                String kbName = n.path("note_book_name").asText(n.path("kb_name").asText(n.path("name").asText("")));
                if (kbId.isBlank() && kbName.isBlank()) continue;
                String kbType = n.path("type").asText(n.path("kb_type").asText("")).toLowerCase().contains("own") ? "owned" : "subscribed";
                long docCount = n.path("doc_count").asLong(n.path("note_count").asLong(-1));
                out.add(new KBInfo(
                        kbId.isBlank() ? "ima-" + UUID.randomUUID().toString().substring(0, 8) : kbId,
                        kbName.isBlank() ? kbId : kbName,
                        kbType, docCount, Instant.now()));
            }
        } catch (Exception e) {
            log.warn("[IMA] 解析知识库列表响应失败", e);
        }
        return out;
    }

    @Override
    public List<KBUpdateEvent> getUpdates(String kbId, Instant since) {
        return Collections.emptyList();
    }
}
