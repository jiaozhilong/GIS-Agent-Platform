package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.Skill;
import com.gisagent.service.SkillService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 包装外部 Skill 的流水线工具。
 * 运行时替代内置工具节点：调用 API_ENDPOINT 类型 Skill，并把其返回的 JSON 产物
 * 映射回 Context Bus 中对应字段（与内置工具产出同构），供下游节点消费。
 *
 * 注意：本类仅承接 API_ENDPOINT 类型；GIT_REPO 类型由引擎在 resolveTools 阶段过滤（回退内置）。
 */
@Slf4j
public class SkillTool implements PipelineTool {

    private final String toolType;
    private final Skill skill;
    private final SkillService skillService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillTool(String toolType, Skill skill, SkillService skillService) {
        this.toolType = toolType;
        this.skill = skill;
        this.skillService = skillService;
    }

    @Override
    public String getToolType() {
        return toolType;
    }

    @Override
    public boolean execute(ToolContext context, LlmConfig llmConfig) {
        try {
            String json = skillService.executeSkill(skill, toolType, context, llmConfig);
            writeOutput(context, toolType, json);
            log.info("[Skill] 工具 {} 由 Skill#{} 执行完成", toolType, skill.getId());
            return true;
        } catch (Exception e) {
            log.warn("[Skill] 工具 {} 执行失败（Skill#{}）: {}", toolType, skill.getId(), e.getMessage());
            return false;
        }
    }

    /** 将 Skill 返回的 JSON 产物写入 Context Bus 对应字段（与 PipelineEngine.injectOutput 同构） */
    private void writeOutput(ToolContext ctx, String toolType, String outputJson) {
        if (outputJson == null || outputJson.isBlank()) return;
        try {
            switch (toolType) {
                case "REQUIREMENT_ANALYSIS" ->
                        ctx.setRequirements(objectMapper.readValue(outputJson, ToolContext.RequirementResult.class));
                case "PRODUCT_MATCHING" ->
                        ctx.setProductSelection(readListOrSingle(outputJson, ToolContext.ProductSelection.class));
                case "CASE_RECOMMEND" ->
                        ctx.setCaseRecommendations(readListOrSingle(outputJson, ToolContext.CaseRecommendation.class));
                case "COMPETITOR_ANALYSIS" ->
                        ctx.setCompetitorAnalysis(readListOrSingle(outputJson, ToolContext.CompetitorComparison.class));
                case "ARCHITECTURE_DIAGRAM" ->
                        ctx.setArchitectureDiagram(objectMapper.readValue(outputJson, ToolContext.ArchitectureDiagram.class));
                case "SOLUTION_OUTLINE" ->
                        ctx.setSolutionOutline(objectMapper.readValue(outputJson, ToolContext.SolutionOutline.class));
                case "SOLUTION_QC" ->
                        ctx.setQualityCheck(objectMapper.readValue(outputJson, ToolContext.QualityCheck.class));
                case "SOLUTION_OUTPUT" -> {
                    String text = outputJson.trim();
                    if (text.startsWith("\"") && text.endsWith("\"")) {
                        try { ctx.setSolutionText(objectMapper.readValue(text, String.class)); }
                        catch (Exception e) { ctx.setSolutionText(text); }
                    } else {
                        ctx.setSolutionText(text);
                    }
                }
                default -> log.warn("[Skill] 未知工具类型，跳过产物写入: {}", toolType);
            }
        } catch (Exception e) {
            log.warn("[Skill] 产物回注失败: {} - {}", toolType, e.getMessage());
        }
    }

    private <T> List<T> readListOrSingle(String outputJson, Class<T> elementClass) {
        try {
            JsonNode node = objectMapper.readTree(outputJson);
            if (node.isArray()) {
                return objectMapper.convertValue(node,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
            }
            if (node.isObject()) {
                return List.of(objectMapper.convertValue(node, elementClass));
            }
        } catch (Exception e) {
            log.warn("[Skill] 列表型产物解析失败: {}", e.getMessage());
        }
        return List.of();
    }
}
