package com.gisagent.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.*;
import com.gisagent.export.ExportService;
import com.gisagent.repository.*;
import com.gisagent.service.LlmService;
import com.gisagent.util.FileTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线引擎（Phase 2 扩展版）。
 * 模板化工具链执行：quick_selection = 需求分析 → 产品匹配；
 * full_solution = 需求分析 → 产品匹配 → 案例推荐(T2) → 竞品对比(T4)（T5~T9 后续追加）。
 * 使用状态机管理执行状态，通过 Context Bus 传递数据。
 */
@Service
@Slf4j
public class PipelineEngine {

    private final RequirementAnalysisTool requirementAnalysisTool;
    private final ProductMatchingTool productMatchingTool;
    private final CaseRecommendTool caseRecommendTool;
    private final CompetitorTool competitorTool;
    private final ArchitectureDiagramTool architectureDiagramTool;
    private final SolutionOutlineTool solutionOutlineTool;
    private final SolutionQcTool solutionQcTool;
    private final SolutionOutputTool solutionOutputTool;
    private final PipelineRunRepository pipelineRunRepository;
    private final ToolExecutionRepository toolExecutionRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final ExportService exportService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 模板 → 工具执行顺序
    private static final Map<String, List<PipelineTool>> TEMPLATE_TOOLS = new LinkedHashMap<>();

    static {
        List<PipelineTool> quick = new ArrayList<>();
        // 实际工具在构造函数注入后填充
        TEMPLATE_TOOLS.put("quick_selection", quick);
        List<PipelineTool> full = new ArrayList<>();
        TEMPLATE_TOOLS.put("full_solution", full);
    }

    public PipelineEngine(RequirementAnalysisTool requirementAnalysisTool,
                          ProductMatchingTool productMatchingTool,
                          CaseRecommendTool caseRecommendTool,
                          CompetitorTool competitorTool,
                          ArchitectureDiagramTool architectureDiagramTool,
                          SolutionOutlineTool solutionOutlineTool,
                          SolutionQcTool solutionQcTool,
                          SolutionOutputTool solutionOutputTool,
                          PipelineRunRepository pipelineRunRepository,
                          ToolExecutionRepository toolExecutionRepository,
                          ProjectDocumentRepository projectDocumentRepository,
                          ExportService exportService) {
        this.requirementAnalysisTool = requirementAnalysisTool;
        this.productMatchingTool = productMatchingTool;
        this.caseRecommendTool = caseRecommendTool;
        this.competitorTool = competitorTool;
        this.architectureDiagramTool = architectureDiagramTool;
        this.solutionOutlineTool = solutionOutlineTool;
        this.solutionQcTool = solutionQcTool;
        this.solutionOutputTool = solutionOutputTool;
        this.pipelineRunRepository = pipelineRunRepository;
        this.toolExecutionRepository = toolExecutionRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.exportService = exportService;

        // 填充模板工具链
        // 快速选型：需求分析 → 产品匹配
        TEMPLATE_TOOLS.get("quick_selection").add(requirementAnalysisTool);
        TEMPLATE_TOOLS.get("quick_selection").add(productMatchingTool);
        // 全套方案：需求分析 → 产品匹配 → 案例推荐 → 竞品对比
        //          → 架构图 → 方案大纲 → 质检 → 方案输出（T9 PPT 在 P2-4 追加）
        TEMPLATE_TOOLS.get("full_solution").add(requirementAnalysisTool);
        TEMPLATE_TOOLS.get("full_solution").add(productMatchingTool);
        TEMPLATE_TOOLS.get("full_solution").add(caseRecommendTool);
        TEMPLATE_TOOLS.get("full_solution").add(competitorTool);
        TEMPLATE_TOOLS.get("full_solution").add(architectureDiagramTool);
        TEMPLATE_TOOLS.get("full_solution").add(solutionOutlineTool);
        TEMPLATE_TOOLS.get("full_solution").add(solutionQcTool);
        TEMPLATE_TOOLS.get("full_solution").add(solutionOutputTool);
    }

    /**
     * 同步执行流水线（MVP 简化版，无画布拖拽）。
     *
     * @param pipelineRunId 流水线运行记录 ID
     * @param projectId     项目 ID
     * @param templateId    模板 ID
     * @param llmConfig     LLM 配置
     */
    public void run(Long pipelineRunId, Long projectId, String templateId, PipelineTool.LlmConfig llmConfig) {
        PipelineRun run = pipelineRunRepository.findById(pipelineRunId)
                .orElseThrow(() -> new IllegalArgumentException("流水线不存在"));

        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        pipelineRunRepository.save(run);

        List<PipelineTool> tools = TEMPLATE_TOOLS.getOrDefault(templateId, TEMPLATE_TOOLS.get("quick_selection"));

        // 初始化 Context Bus
        ToolContext context = ToolContext.empty();
        context.setRequirementDoc(loadRequirementDoc(projectId));

        boolean anyFailed = false;
        int order = 0;
        for (PipelineTool tool : tools) {
            ToolExecution exec = createToolExecution(run.getId(), tool.getToolType(), order++);
            exec.setStatus("RUNNING");
            exec.setStartedAt(Instant.now());
            exec.setLlmModel(llmConfig.model);
            toolExecutionRepository.save(exec);

            boolean success;
            try {
                success = tool.execute(context, llmConfig);
            } catch (Exception e) {
                log.error("工具执行异常: {}", tool.getToolType(), e);
                success = false;
            }

            exec.setFinishedAt(Instant.now());
            exec.setStatus(success ? "SUCCESS" : "FAILED");
            exec.setOutputJson(toJson(safeOutput(context, tool.getToolType())));
            toolExecutionRepository.save(exec);

            if (!success) {
                anyFailed = true;
                // MVP 策略：Tool-1 失败则整体失败；Tool-3 失败则 PARTIAL
                if ("REQUIREMENT_ANALYSIS".equals(tool.getToolType())) {
                    run.setStatus("FAILED");
                    run.setErrorMessage("需求分析失败");
                    break;
                }
            }
        }

        if (!"FAILED".equals(run.getStatus())) {
            run.setStatus(anyFailed ? "PARTIAL" : "SUCCESS");
        }
        run.setFinishedAt(Instant.now());
        run.setContextJson(toJson(context.toMap()));
        pipelineRunRepository.save(run);

        log.info("流水线执行完成: runId={}, status={}", pipelineRunId, run.getStatus());
    }

    /**
     * 生成导出文件（在流水线成功后调用）。
     */
    public void export(Long projectId, String projectName, Long pipelineRunId) {
        PipelineRun run = pipelineRunRepository.findById(pipelineRunId).orElse(null);
        if (run == null || run.getContextJson() == null) return;
        ToolContext context = parseContext(run.getContextJson());
        // 导出由 Controller 调用 ExportService 完成，这里留空
    }

    private String loadRequirementDoc(Long projectId) {
        List<ProjectDocument> docs = projectDocumentRepository.findByProjectId(projectId);
        if (docs.isEmpty()) return "";
        return FileTextExtractor.extract(docs.get(0).getFilePath());
    }

    private ToolExecution createToolExecution(Long runId, String toolType, int order) {
        return ToolExecution.builder()
                .pipelineRunId(runId)
                .toolType(toolType)
                .toolOrder(order)
                .status("PENDING")
                .build();
    }

    private Object safeOutput(ToolContext context, String toolType) {
        if ("REQUIREMENT_ANALYSIS".equals(toolType)) return context.getRequirements();
        if ("PRODUCT_MATCHING".equals(toolType)) return context.getProductSelection();
        if ("CASE_RECOMMEND".equals(toolType)) return context.getCaseRecommendations();
        if ("COMPETITOR_ANALYSIS".equals(toolType)) return context.getCompetitorAnalysis();
        if ("ARCHITECTURE_DIAGRAM".equals(toolType)) return context.getArchitectureDiagram();
        if ("SOLUTION_OUTLINE".equals(toolType)) return context.getSolutionOutline();
        if ("SOLUTION_QC".equals(toolType)) return context.getQualityCheck();
        if ("SOLUTION_OUTPUT".equals(toolType)) return context.getSolutionText();
        return null;
    }

    private ToolContext parseContext(String json) {
        try {
            return objectMapper.readValue(json, ToolContext.class);
        } catch (Exception e) {
            return ToolContext.empty();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
