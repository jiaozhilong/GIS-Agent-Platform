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
 * Tool-4：竞品对比。
 * 在 IMA 竞品知识库检索 → LLM 生成 SuperMap 与竞品的对比分析 → 写入 context.competitorAnalysis
 */
@Component
@Slf4j
public class CompetitorTool implements PipelineTool {

    private final LlmService llmService;
    private final ImaSearchService imaSearchService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个 GIS 行业竞争分析专家。基于用户的需求与已推荐产品，以及从知识库检索到的竞品资料，
        生成 SuperMap 与主要竞品（如 ArcGIS）的对比分析。严格按照 JSON 格式输出，不要包含额外说明文字。

        输出 JSON schema:
        {
          "comparisons": [
            {
              "competitorName": "竞品名称（如 ArcGIS）",
              "ourAdvantage": "SuperMap 的优势点",
              "ourDisadvantage": "SuperMap 的相对劣势或需注意点",
              "recommendation": "面向该需求的应对建议",
              "advantageScore": 88,
              "referenceDoc": "来源知识库文档名（从检索到的资料标题中选取最相关的一条）"
            }
          ]
        }
        说明：advantageScore 为 SuperMap 相对该竞品在本需求下的优势信心分（0-100，越高越占优）；referenceDoc 必须取自下方"知识库检索到的竞品资料"中的某条标题。
        """;

    public CompetitorTool(LlmService llmService, ImaSearchService imaSearchService) {
        this.llmService = llmService;
        this.imaSearchService = imaSearchService;
    }

    @Override
    public String getToolType() {
        return "COMPETITOR_ANALYSIS";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳过竞品对比");
            return false;
        }

        String query = buildQuery(context);
        String retrieved = retrieveFromIma(context.getUserId(), query);

        String userPrompt = """
            需求清单：
            - 功能需求：%s
            - 行业场景：%s
            已推荐产品：%s

            知识库检索到的竞品资料：
            %s

            请生成 SuperMap 与竞品的对比分析。若知识库检索结果为"（无检索结果）"或"（检索失败）"，
            请基于公开的 GIS 行业竞争认知生成对比，并将 referenceDoc 标记为"行业公开经验"。
            """.formatted(
                context.getRequirements().getFunctional(),
                context.getRequirements().getIndustry(),
                context.getProductSelection(),
                retrieved
        );

        com.gisagent.service.CompletionResult cr = llmService.completeWithUsage(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.3, 2048);
        String raw = cr.content();
        context.addUsage(cr.usage());

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            JsonNode comparisons = node.path("comparisons");
            List<ToolContext.CompetitorComparison> list = new ArrayList<>();
            if (comparisons.isArray()) {
                for (JsonNode c : comparisons) {
                    ToolContext.CompetitorComparison r = new ToolContext.CompetitorComparison();
                    r.setCompetitorName(c.path("competitorName").asText());
                    r.setOurAdvantage(c.path("ourAdvantage").asText());
                    r.setOurDisadvantage(c.path("ourDisadvantage").asText());
                    r.setRecommendation(c.path("recommendation").asText());
                    r.setAdvantageScore(c.path("advantageScore").asDouble(0.0));
                    r.setReferenceDoc(c.path("referenceDoc").asText());
                    list.add(r);
                }
            }
            context.setCompetitorAnalysis(list);
            log.info("竞品对比完成，对比 {} 个竞品", list.size());
            return !list.isEmpty();
        } catch (Exception e) {
            log.error("解析竞品对比结果失败", e);
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

    private String retrieveFromIma(Long userId, String query) {
        // 按当前用户隔离：使用用户自己的 IMA 凭证 + purpose=competitor 的启用知识库
        return imaSearchService.retrieve(userId, "competitor", query, 5);
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
