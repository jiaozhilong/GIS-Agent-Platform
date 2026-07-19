package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool-1：需求分析。
 * 读取需求文档 → LLM 提取结构化需求 → 写入 context.requirements
 */
@Component
@Slf4j
public class RequirementAnalysisTool implements PipelineTool {

    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个资深的 GIS 行业解决方案分析师。请从用户提供的客户需求文档中，
        提取结构化的需求清单。严格按照 JSON 格式输出，不要包含任何额外说明文字。

        输出 JSON schema:
        {
          "functional": ["功能需求1", "功能需求2", ...],
          "nonFunctional": ["非功能需求1", ...],
          "constraints": ["约束条件1", ...],
          "industry": "行业场景（如：智慧城市/国土空间规划/自然资源/低空经济）"
        }
        """;

    public RequirementAnalysisTool(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getToolType() {
        return "REQUIREMENT_ANALYSIS";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirementDoc() == null || context.getRequirementDoc().isBlank()) {
            log.warn("需求文档为空，跳过需求分析");
            return false;
        }

        String userPrompt = "请分析以下客户需求文档，提取结构化需求：\n\n"
                + context.getRequirementDoc();

        com.gisagent.service.CompletionResult r = llmService.completeWithUsage(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, llmConfig.temperature, llmConfig.maxTokens);
        String raw = r.content();
        context.addUsage(r.usage());

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            ToolContext.RequirementResult result = new ToolContext.RequirementResult();
            result.setFunctional(readArray(node, "functional"));
            result.setNonFunctional(readArray(node, "nonFunctional"));
            result.setConstraints(readArray(node, "constraints"));
            result.setIndustry(node.path("industry").asText("未指定"));
            context.setRequirements(result);
            log.info("需求分析完成，行业场景：{}", result.getIndustry());
            return true;
        } catch (Exception e) {
            log.error("解析需求分析结果失败", e);
            return false;
        }
    }

    private java.util.List<String> readArray(JsonNode node, String field) {
        java.util.List<String> list = new java.util.ArrayList<>();
        JsonNode arr = node.path(field);
        if (arr.isArray()) {
            arr.forEach(n -> list.add(n.asText()));
        }
        return list;
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
