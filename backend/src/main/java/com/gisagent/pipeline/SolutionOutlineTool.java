package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-6：方案框架。
 * 汇总需求/产品/案例/竞品，调用 LLM 生成方案大纲结构，写入 context.solutionOutline
 */
@Component
@Slf4j
public class SolutionOutlineTool implements PipelineTool {

    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个 GIS 解决方案架构师。基于已有需求分析、产品选型、案例推荐和竞品对比，
        生成一份完整的解决方案大纲。严格按照 JSON 格式输出，不要包含额外说明文字。

        输出 JSON schema:
        {
          "overview": "方案总体概述（2-3 句）",
          "sections": [
            { "title": "章节标题", "keyPoints": "该章节要点（分点描述）" }
          ]
        }
        """;

    public SolutionOutlineTool(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getToolType() {
        return "SOLUTION_OUTLINE";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳过大纲生成");
            return false;
        }

        String suggestions = "";
        if (context.getQualityCheck() != null
                && context.getQualityCheck().getSuggestions() != null
                && !context.getQualityCheck().getSuggestions().isEmpty()) {
            suggestions = "\n\n上一轮质检的改进建议（请在本轮大纲中落实，针对性补强相关章节）：\n- "
                    + String.join("\n- ", context.getQualityCheck().getSuggestions());
        }

        String userPrompt = """
            需求分析：%s
            产品选型：%s
            案例推荐：%s
            竞品对比：%s%s

            请生成解决方案大纲。
            """.formatted(
                context.getRequirements(),
                context.getProductSelection(),
                context.getCaseRecommendations(),
                context.getCompetitorAnalysis(),
                suggestions
        );

        com.gisagent.service.CompletionResult r = llmService.completeWithUsage(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.4, 2048);
        String raw = r.content();
        context.addUsage(r.usage());

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            ToolContext.SolutionOutline outline = new ToolContext.SolutionOutline();
            outline.setOverview(node.path("overview").asText());
            List<ToolContext.OutlineSection> sections = new ArrayList<>();
            JsonNode arr = node.path("sections");
            if (arr.isArray()) {
                for (JsonNode s : arr) {
                    ToolContext.OutlineSection sec = new ToolContext.OutlineSection();
                    sec.setTitle(s.path("title").asText());
                    sec.setKeyPoints(s.path("keyPoints").asText());
                    sections.add(sec);
                }
            }
            outline.setSections(sections);
            context.setSolutionOutline(outline);
            log.info("方案大纲生成完成，共 {} 章", sections.size());
            return !sections.isEmpty();
        } catch (Exception e) {
            log.error("解析方案大纲失败", e);
            return false;
        }
    }

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
