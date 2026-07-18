package com.gisagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.LlmProvider;
import com.gisagent.pipeline.PipelineTool;
import com.gisagent.pipeline.ToolCatalog;
import com.gisagent.repository.LlmProviderRepository;
import com.gisagent.util.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 自编排（P4-5）：根据用户自然语言需求，调用 LLM 从已注册工具中推荐有序工具链。
 * - 无 LLM Provider → 400（与流水线运行一致）。
 * - LLM 调用或解析失败 → 优雅降级为标准全链路（保证始终返回可用结果，不 500）。
 * 推荐结果仅包含已注册工具类型，并保持 LLM 给出的顺序。
 */
@Service
@Slf4j
public class OrchestrationService {

    private final LlmService llmService;
    private final LlmProviderRepository llmProviderRepository;
    private final List<PipelineTool> tools;
    private final ToolCatalog catalog;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrchestrationService(LlmService llmService,
                                LlmProviderRepository llmProviderRepository,
                                List<PipelineTool> tools,
                                ToolCatalog catalog,
                                EncryptionService encryptionService) {
        this.llmService = llmService;
        this.llmProviderRepository = llmProviderRepository;
        this.tools = tools;
        this.catalog = catalog;
        this.encryptionService = encryptionService;
    }

    public Map<String, Object> recommend(Long userId, String requirement) {
        if (requirement == null || requirement.trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "需求描述不能为空");
        }
        requirement = requirement.trim();

        // 解析用户默认 LLM Provider（与 PipelineController 一致）
        Optional<LlmProvider> providerOpt = llmProviderRepository.findByUserIdAndIsDefaultTrue(userId).stream().findFirst();
        if (providerOpt.isEmpty()) providerOpt = llmProviderRepository.findByUserId(userId).stream().findFirst();
        if (providerOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置 LLM Provider");
        }
        LlmProvider provider = providerOpt.get();
        // 解密 key 后再调用（F-A 加密存储；run 路径同样需用解密值）
        String apiKey = encryptionService.decrypt(provider.getApiKeyEncrypted());
        String model = defaultModel(provider);

        List<String> registered = tools.stream().map(PipelineTool::getToolType).toList();
        String catalogText = catalog.toPrompt(registered);

        String system = "你是 GIS 方案生成平台的「流程编排助手」。"
                + "用户会给出方案需求描述，你需要从给定的可用工具中选择一个合适的、有序的执行链路，"
                + "以最少必要步骤覆盖用户需求。只输出 JSON，不要包含解释性文字或 Markdown 代码块标记。";
        String user = "可用工具（按推荐优先级列出）：\n" + catalogText
                + "\n用户需求：\n" + requirement
                + "\n请返回 JSON，格式：{\"reason\": \"选择理由（中文，一句话）\", \"toolChain\": [\"TOOL_TYPE\", ...]}"
                + "，toolChain 中的值必须是上面列出的 [TOOL_TYPE] 之一，且按执行顺序排列。";

        try {
            String raw = llmService.complete(provider.getEndpoint(), apiKey, model, system, user, 0.2, 1024);
            Map<String, Object> parsed = parse(raw, registered);
            if (parsed != null) {
                parsed.put("model", model);
                parsed.put("usedFallback", false);
                log.info("Agent 自编排成功：requirement={}, chain={}", truncate(requirement), parsed.get("toolChain"));
                return parsed;
            }
        } catch (Exception e) {
            log.warn("Agent 自编排 LLM 调用失败，使用标准链路降级：{}", e.getMessage());
        }
        // 降级：标准全链路
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("reason", "已使用默认推荐链路（LLM 暂不可用或返回无法解析）");
        fallback.put("toolChain", catalog.orderedTypes(registered));
        fallback.put("model", model);
        fallback.put("usedFallback", true);
        return fallback;
    }

    /** 从 LLM 文本中解析推荐 JSON，并过滤为已注册工具类型、保持顺序 */
    private Map<String, Object> parse(String raw, List<String> registered) {
        if (raw == null || raw.isBlank()) return null;
        String json = extractJson(raw);
        if (json == null) return null;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode chain = root.path("toolChain");
            if (!chain.isArray() || chain.size() == 0) return null;
            Set<String> known = new HashSet<>(registered);
            List<String> types = new ArrayList<>();
            for (JsonNode n : chain) {
                String t = n.asText();
                if (t != null && known.contains(t) && !types.contains(t)) types.add(t);
            }
            if (types.isEmpty()) return null;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reason", root.path("reason").asText(""));
            result.put("toolChain", types);
            return result;
        } catch (Exception e) {
            log.warn("解析自编排 JSON 失败：{}", e.getMessage());
            return null;
        }
    }

    /** 去掉 ```json ... ``` 围栏，返回纯 JSON 文本 */
    private String extractJson(String raw) {
        String s = raw.trim();
        // 去掉可能的 Markdown 代码块围栏
        int fence = s.indexOf("```");
        if (fence >= 0) {
            int end = s.lastIndexOf("```");
            int start = s.indexOf('\n', fence) + 1;
            if (end > start) s = s.substring(start, end);
        }
        // 截取第一个 { 到最后一个 } 之间的内容
        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first >= 0 && last > first) s = s.substring(first, last + 1);
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private String defaultModel(LlmProvider provider) {
        if (provider.getModel() != null && !provider.getModel().isBlank()) return provider.getModel();
        String ep = provider.getEndpoint() == null ? "" : provider.getEndpoint().toLowerCase();
        if (ep.contains("deepseek")) return "deepseek-chat";
        if (ep.contains("openai.com")) return "gpt-4o";
        if (ep.contains("qwen") || ep.contains("dashscope")) return "qwen-max";
        return "gpt-4o";
    }

    private String truncate(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
