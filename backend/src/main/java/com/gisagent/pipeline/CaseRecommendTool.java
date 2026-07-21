package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.service.ImaSearchService;
import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-2：案例推荐。
 * 在 IMA 案例知识库检索 → LLM 综合推荐相关落地案例 → 写入 context.caseRecommendations
 */
@Component
@Slf4j
public class CaseRecommendTool implements PipelineTool {

    private final LlmService llmService;
    private final ImaSearchService imaSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个 SuperMap GIS 方案咨询专家。基于用户的需求清单和从知识库检索到的案例资料，
        推荐最相关的行业落地案例。严格按照 JSON 格式输出，不要包含任何额外说明文字。

        输出 JSON schema:
        {
          "cases": [
            {
              "caseName": "案例名称",
              "scenario": "适用场景/行业",
              "productsUsed": "使用的核心产品（如 SuperMap iManager、iServer）",
              "keyEffect": "项目成效或业务价值",
              "matchReason": "与当前需求的匹配点",
              "matchScore": 92,
              "referenceDoc": "来源知识库文档名（从检索到的资料标题中选取最相关的一条）"
            }
          ]
        }
        说明：matchScore 为该案例与当前需求的相关度（0-100，越高越相关）；referenceDoc 必须取自下方"知识库检索到的案例资料"中的某条标题。
        """;

    public CaseRecommendTool(LlmService llmService, ImaSearchService imaSearchService) {
        this.llmService = llmService;
        this.imaSearchService = imaSearchService;
    }

    @Override
    public String getToolType() {
        return "CASE_RECOMMEND";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳过案例推荐");
            return false;
        }

        String query = buildQuery(context);
        String retrieved = retrieveFromIma(context, query);

        String userPrompt = """
            需求清单：
            - 功能需求：%s
            - 行业场景：%s

            知识库检索到的案例资料：
            %s

            请基于以上信息推荐最相关的落地案例。若知识库检索结果为"（无检索结果）"或"（检索失败）"，
            请基于 SuperMap GIS 公开的行业落地经验进行推荐，并将 referenceDoc 标记为"行业公开经验"。
            """.formatted(
                context.getRequirements().getFunctional(),
                context.getRequirements().getIndustry(),
                retrieved
        );

        com.gisagent.service.CompletionResult cr = llmService.completeWithUsage(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.3, 2048);
        String raw = cr.content();
        context.addUsage(cr.usage());

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            JsonNode cases = node.path("cases");
            List<ToolContext.CaseRecommendation> list = new ArrayList<>();
            if (cases.isArray()) {
                for (JsonNode c : cases) {
                    ToolContext.CaseRecommendation r = new ToolContext.CaseRecommendation();
                    r.setCaseName(c.path("caseName").asText());
                    r.setScenario(c.path("scenario").asText());
                    r.setProductsUsed(c.path("productsUsed").asText());
                    r.setKeyEffect(c.path("keyEffect").asText());
                    r.setMatchReason(c.path("matchReason").asText());
                    r.setMatchScore(c.path("matchScore").asDouble(0.0));
                    r.setReferenceDoc(c.path("referenceDoc").asText());
                    list.add(r);
                }
            }
            context.setCaseRecommendations(list);
            log.info("案例推荐完成，推荐 {} 个案例", list.size());
            return !list.isEmpty();
        } catch (Exception e) {
            log.error("解析案例推荐结果失败", e);
            return false;
        }
    }

    private String buildQuery(ToolContext context) {
        List<String> parts = new ArrayList<>();
        if (context.getRequirements().getFunctional() != null) {
            parts.addAll(context.getRequirements().getFunctional());
        }
        if (context.getRequirements().getIndustry() != null) {
            parts.add(context.getRequirements().getIndustry());
        }
        return String.join(", ", parts);
    }

    private String retrieveFromIma(ToolContext context, String query) {
        // 按当前用户隔离：使用用户自己的 IMA 凭证 + purpose=case_doc 的启用知识库
        return imaSearchService.retrieve(context.getUserId(), "case_doc", query, 5, context.getKbConfigIds());
    }

    /** 从 LLM 返回中提取 JSON（去掉 markdown 代码块标记） */
    private String extractJson(String text) {
        if (text == null) return "{}";
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            int lastBacktick = t.lastIndexOf("```");
            if (firstNewline > 0 && lastBacktick > firstNewline) {
                t = t.substring(firstNewline + 1, lastBacktick).trim();
            }
        }
        return t;
    }
}
