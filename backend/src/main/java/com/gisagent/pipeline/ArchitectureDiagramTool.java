package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool-5：架构图生成。
 * 基于需求与产品选型，调用 LLM 生成方案技术架构描述与 Mermaid 架构图源码，
 * 写入 context.architectureDiagram
 */
@Component
@Slf4j
public class ArchitectureDiagramTool implements PipelineTool {

    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个 GIS 平台架构师。基于需求清单与推荐产品，设计一套技术架构方案，
        并输出 Mermaid 流程图/架构图源码。严格按照 JSON 格式输出，不要包含额外说明文字。

        输出 JSON schema:
        {
          "title": "架构图标题",
          "mermaid": "Mermaid 语法源码（如 graph TD; A[数据接入] --> B[存储] ...）",
          "description": "架构设计说明（分点描述各层职责）"
        }
        """;

    public ArchitectureDiagramTool(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getToolType() {
        return "ARCHITECTURE_DIAGRAM";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳过架构图生成");
            return false;
        }

        String userPrompt = """
            需求清单：
            - 功能需求：%s
            - 非功能需求：%s
            - 行业场景：%s
            推荐产品：%s

            请设计技术架构并输出 Mermaid 架构图源码。
            """.formatted(
                context.getRequirements().getFunctional(),
                context.getRequirements().getNonFunctional(),
                context.getRequirements().getIndustry(),
                context.getProductSelection()
        );

        String raw = llmService.complete(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.3, 2048);

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            ToolContext.ArchitectureDiagram diagram = new ToolContext.ArchitectureDiagram();
            diagram.setTitle(node.path("title").asText("技术方案架构"));
            diagram.setMermaid(node.path("mermaid").asText());
            diagram.setDescription(node.path("description").asText());
            context.setArchitectureDiagram(diagram);
            log.info("架构图生成完成");
            return diagram.getMermaid() != null && !diagram.getMermaid().isBlank();
        } catch (Exception e) {
            log.error("解析架构图结果失败", e);
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
