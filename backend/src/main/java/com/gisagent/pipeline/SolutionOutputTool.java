package com.gisagent.pipeline;

import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tool-8：方案输出。
 * 汇总前面所有工具的结构化结果，调用 LLM 整合成一份连贯的 Markdown 解决方案文档，
 * 写入 context.solutionText（最终交付文本）
 */
@Component
@Slf4j
public class SolutionOutputTool implements PipelineTool {

    private final LlmService llmService;

    private static final String SYSTEM_PROMPT = """
        你是一个资深的 GIS 解决方案撰写专家。请将提供的结构化分析结果，整合成一份
        连贯、专业、可直接交付客户的解决方案文档（Markdown 格式）。
        结构应包含：项目背景与需求、产品选型、技术架构（含 Mermaid）、参考案例、
        竞品对比、实施建议。语言专业、条理清晰，不要输出额外说明文字。
        """;

    public SolutionOutputTool(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getToolType() {
        return "SOLUTION_OUTPUT";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳過方案输出");
            return false;
        }

        String userPrompt = """
            请基于以下结构化结果生成完整解决方案文档（Markdown）：

            【需求分析】
            %s

            【产品选型】
            %s

            【参考案例】
            %s

            【竞品对比】
            %s

            【技术架构】
            标题：%s
            Mermaid：%s
            说明：%s

            【方案大纲】
            %s

            【质检结果】
            整体分：%s
            建议：%s
            """.formatted(
                context.getRequirements(),
                context.getProductSelection(),
                context.getCaseRecommendations(),
                context.getCompetitorAnalysis(),
                context.getArchitectureDiagram() != null ? context.getArchitectureDiagram().getTitle() : "（无）",
                context.getArchitectureDiagram() != null ? context.getArchitectureDiagram().getMermaid() : "（无）",
                context.getArchitectureDiagram() != null ? context.getArchitectureDiagram().getDescription() : "（无）",
                context.getSolutionOutline(),
                context.getQualityCheck() != null ? context.getQualityCheck().getOverallScore() : "（未质检）",
                context.getQualityCheck() != null ? context.getQualityCheck().getSuggestions() : "（无）"
        );

        String raw = llmService.complete(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.5, 4096);

        if (raw == null || raw.isBlank()) {
            log.warn("方案输出为空");
            return false;
        }
        context.setSolutionText(raw.trim());
        log.info("方案输出完成，文档长度={}", raw.length());
        return true;
    }
}
