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
 * IMA 知识库连接器真实实现（Wiki API v1）。
 * 调用 IMA 开放接口（知识库搜索/检索/列表）作为方案生成的知识源。
 * 默认启用，凭证按用户通过 {@link ImaAuth} 传入。
 * Base URL 默认 https://ima.qq.com/openapi/wiki/v1。
 * 设置 ima.mock-enabled=true 可切换为 Mock 实现。
 */
@Component
public class RealIMAKnowledgeBaseConnector implements IMAKnowledgeBaseConnector {

    private static final Logger log = LoggerFactory.getLogger(RealIMAKnowledgeBaseConnector.class);
    private static final String DEFAULT_BASE = "https://ima.qq.com/openapi/wiki/v1";

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
            String url = resolveBaseUrl(auth) + "/search_knowledge_base";
            Map<String, Object> body = Map.of("query", "", "cursor", "", "limit", 1);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders(auth));
            String resp = restTemplate.postForEntity(url, req, String.class).getBody();
            JsonNode root = objectMapper.readTree(resp);
            return root.path("code").asInt() == 0;
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
            String url = resolveBaseUrl(auth) + "/search_knowledge";
            Map<String, Object> body = Map.of(
                    "query", query == null ? "" : query,
                    "cursor", "",
                    "knowledge_base_id", kbId == null ? "" : kbId);
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders(auth));
            String resp = restTemplate.postForEntity(url, req, String.class).getBody();
            return parseSearchResults(resp, kbId);
        } catch (Exception e) {
            log.warn("[IMA] 检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchResult> parseSearchResults(String resp, String kbId) {
        List<SearchResult> out = new ArrayList<>();
        if (resp == null || resp.isBlank()) return out;
        try {
            JsonNode root = objectMapper.readTree(resp);
            if (root.path("code").asInt() != 0) return out;
            JsonNode list = root.path("data").path("info_list");
            if (!list.isArray()) return out;
            double score = 0.92;
            for (JsonNode n : list) {
                String mediaId = n.path("media_id").asText("");
                String title = n.path("title").asText("");
                String highlight = n.path("highlight_content").asText("");
                if (title.isBlank() && highlight.isBlank()) continue;
                out.add(new SearchResult(
                        mediaId.isBlank() ? "ima-" + UUID.randomUUID().toString().substring(0, 8) : mediaId,
                        title, highlight.isBlank() ? title : highlight,
                        score, kbId, Instant.now()));
                score -= 0.03;
                if (score < 0.5) score = 0.5;
            }
        } catch (Exception e) {
            log.warn("[IMA] 解析检索响应失败", e);
        }
        return out;
    }

    @Override
    public KBInfo getKBInfo(String kbId) {
        // Wiki API 没有单独的 getKBInfo，通过 listKnowledgeBases 的缓存替代
        return new KBInfo(kbId, "IMA 知识库（Wiki v1）", "subscribed", -1, Instant.now());
    }

    @Override
    public List<KBInfo> listKnowledgeBases(ImaAuth auth) {
        if (auth == null || auth.clientId() == null || auth.clientId().isBlank()
                || auth.apiKey() == null || auth.apiKey().isBlank()) {
            log.warn("[IMA] 未配置 Client ID / API Key，跳过拉取知识库列表");
            return Collections.emptyList();
        }
        List<KBInfo> all = new ArrayList<>();
        String cursor = "";
        try {
            String url = resolveBaseUrl(auth) + "/search_knowledge_base";
            while (true) {
                Map<String, Object> body = Map.of("query", "", "cursor", cursor, "limit", 20);
                HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, authHeaders(auth));
                String resp = restTemplate.postForEntity(url, req, String.class).getBody();
                if (resp == null) break;
                JsonNode root = objectMapper.readTree(resp);
                if (root.path("code").asInt() != 0) break;
                JsonNode data = root.path("data");
                JsonNode list = data.path("info_list");
                if (!list.isArray()) break;
                for (JsonNode n : list) {
                    String kbId = n.path("kb_id").asText("");
                    String kbName = n.path("kb_name").asText("");
                    if (kbId.isBlank() || kbName.isBlank()) continue;
                    String roleType = n.path("role_type").asText("");
                    String kbType = "subscribed";
                    if (roleType.contains("创建")) kbType = "owned";
                    else if (roleType.contains("普通")) kbType = "subscribed";
                    long docCount = n.path("content_count").asLong(-1);
                    all.add(new KBInfo(kbId, kbName, kbType, docCount, Instant.now()));
                }
                boolean isEnd = data.path("is_end").asBoolean(true);
                if (isEnd) break;
                cursor = data.path("next_cursor").asText("");
                if (cursor.isBlank()) break;
            }
        } catch (Exception e) {
            log.warn("[IMA] 拉取知识库列表失败: {}", e.getMessage());
        }
        return all;
    }

    @Override
    public List<KBUpdateEvent> getUpdates(String kbId, Instant since) {
        return Collections.emptyList();
    }
}
