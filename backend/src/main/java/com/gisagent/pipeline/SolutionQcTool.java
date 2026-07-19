package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-7：方案质检。
 * 对当前方案上下文做多维度评分，给出整体分、各维度得分与改进建议，
 * 写入 context.qualityCheck
 */
@Component
@Slf4j
public class SolutionQcTool implements PipelineTool {

    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        你是一个严格的 GIS 解决方案评审专家。基于已有分析结果，对方案做多维度质量评审。
        严格按照 JSON 格式输出，不要包含额外说明文字。整体分与各维度分均为 0-100 的数值。

        输出 JSON schema:
        {
          "overallScore": 85,
          "passed": true,
          "level": "良好",
          "dimensions": [
            { "dimension": "需求覆盖度", "score": 90, "comment": "点评" }
          ],
          "suggestions": ["改进建议1", "改进建议2"]
        }
        评级规则：整体分 ≥ 90 为"优秀"，≥ 80 为"良好"，≥ 70 为"合格"，< 70 为"待改进"；
        整体分 ≥ 75 时 passed 为 true，否则为 false。suggestions 是针对低分维度可落地的改进点。
        """;

    public SolutionQcTool(LlmService llmService) {
        this.llmService = llmService;
    }

    @Override
    public String getToolType() {
        return "SOLUTION_QC";
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        if (context.getRequirements() == null) {
            log.warn("需求清单为空，跳过质检");
            return false;
        }

        String userPrompt = """
            需求分析：%s
            产品选型：%s
            案例推荐：%s
            竞品对比：%s
            架构图：%s
            方案大纲：%s

            请对以上方案做质量评审。
            """.formatted(
                context.getRequirements(),
                context.getProductSelection(),
                context.getCaseRecommendations(),
                context.getCompetitorAnalysis(),
                context.getArchitectureDiagram(),
                context.getSolutionOutline()
        );

        com.gisagent.service.CompletionResult r = llmService.completeWithUsage(
                llmConfig.endpoint, llmConfig.apiKey, llmConfig.model,
                SYSTEM_PROMPT, userPrompt, 0.2, 2048);
        String raw = r.content();
        context.addUsage(r.usage());

        try {
            JsonNode node = objectMapper.readTree(extractJson(raw));
            ToolContext.QualityCheck qc = new ToolContext.QualityCheck();
            qc.setOverallScore(node.path("overallScore").asDouble(0.0));
            List<ToolContext.DimensionScore> dims = new ArrayList<>();
            JsonNode arr = node.path("dimensions");
            if (arr.isArray()) {
                for (JsonNode d : arr) {
                    ToolContext.DimensionScore ds = new ToolContext.DimensionScore();
                    ds.setDimension(d.path("dimension").asText());
                    ds.setScore(d.path("score").asDouble(0.0));
                    ds.setComment(d.path("comment").asText());
                    dims.add(ds);
                }
            }
            qc.setDimensions(dims);
            List<String> suggestions = new ArrayList<>();
            JsonNode sug = node.path("suggestions");
            if (sug.isArray()) {
                sug.forEach(s -> suggestions.add(s.asText()));
            }
            qc.setSuggestions(suggestions);
            double overall = qc.getOverallScore();
            qc.setPassed(overall >= 75.0);
            qc.setLevel(overall >= 90 ? "优秀" : overall >= 80 ? "良好" : overall >= 70 ? "合格" : "待改进");
            context.setQualityCheck(qc);
            log.info("方案质检完成，整体分={}，等级={}，通过={}", overall, qc.getLevel(), qc.getPassed());
            return true;
        } catch (Exception e) {
            log.error("解析质检结果失败", e);
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
