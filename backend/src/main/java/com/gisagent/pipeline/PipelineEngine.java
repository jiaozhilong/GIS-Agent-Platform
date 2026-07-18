package com.gisagent.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.*;
import com.gisagent.export.ExportService;
import com.gisagent.repository.*;
import com.gisagent.service.LlmService;
import com.gisagent.util.FileTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * 流水线引擎（Phase 2 扩展版）。
 * 模板化工具链执行：每个模板在 pipeline_templates 表中定义 toolChain（toolType 序列），
 * 引擎按 templateId 解析为实际工具实例序列执行；DB 模板缺失时回退 quick/full 硬编码链。
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
    private final PipelineTemplateRepository templateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** toolType → 实际工具实例，用于按模板工具链解析 */
    private final Map<String, PipelineTool> TOOL_BY_TYPE = new HashMap<>();

    /** 回退工具链（DB 模板缺失时） */
    private static final Map<String, List<PipelineTool>> FALLBACK_TOOLS = new LinkedHashMap<>();

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
                          ExportService exportService,
                          PipelineTemplateRepository templateRepository) {
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
        this.templateRepository = templateRepository;

        TOOL_BY_TYPE.put("REQUIREMENT_ANALYSIS", requirementAnalysisTool);
        TOOL_BY_TYPE.put("PRODUCT_MATCHING", productMatchingTool);
        TOOL_BY_TYPE.put("CASE_RECOMMEND", caseRecommendTool);
        TOOL_BY_TYPE.put("COMPETITOR_ANALYSIS", competitorTool);
        TOOL_BY_TYPE.put("ARCHITECTURE_DIAGRAM", architectureDiagramTool);
        TOOL_BY_TYPE.put("SOLUTION_OUTLINE", solutionOutlineTool);
        TOOL_BY_TYPE.put("SOLUTION_QC", solutionQcTool);
        TOOL_BY_TYPE.put("SOLUTION_OUTPUT", solutionOutputTool);

        // 回退链：数据与 DB 预置模板一致，仅在模板表缺失时兜底
        FALLBACK_TOOLS.put("quick_selection",
                List.of(requirementAnalysisTool, productMatchingTool));
        FALLBACK_TOOLS.put("full_solution",
                List.of(requirementAnalysisTool, productMatchingTool, caseRecommendTool,
                        competitorTool, architectureDiagramTool, solutionOutlineTool,
                        solutionQcTool, solutionOutputTool));
    }

    /**
     * 同步执行流水线（MVP 简化版，无画布拖拽）。
     *
     * @param pipelineRunId 流水线运行记录 ID
     * @param projectId     项目 ID
     * @param templateId    模板 key
     * @param llmConfig     LLM 配置
     */
    public void run(Long pipelineRunId, Long projectId, String templateId, PipelineTool.LlmConfig llmConfig) {
        PipelineRun run = pipelineRunRepository.findById(pipelineRunId)
                .orElseThrow(() -> new IllegalArgumentException("流水线不存在"));

        run.setStatus("RUNNING");
        run.setStartedAt(Instant.now());
        pipelineRunRepository.save(run);

        List<PipelineTool> tools = resolveTools(templateId);

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
                // MVP 策略：Tool-1 失败则整体失败；其余失败则 PARTIAL
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
     * 根据模板 key 解析工具链：DB 模板优先，缺失时回退硬编码链。
     */
    private List<PipelineTool> resolveTools(String templateId) {
        if (templateId != null) {
            Optional<PipelineTemplate> tpl = templateRepository.findByTemplateKey(templateId);
            if (tpl.isPresent() && tpl.get().getToolChainJson() != null) {
                try {
                    List<String> chain = objectMapper.readValue(
                            tpl.get().getToolChainJson(), new TypeReference<List<String>>() {});
                    List<PipelineTool> tools = new ArrayList<>();
                    for (String type : chain) {
                        PipelineTool tool = TOOL_BY_TYPE.get(type);
                        if (tool != null) tools.add(tool);
                    }
                    if (!tools.isEmpty()) {
                        log.info("按模板 {} 解析出 {} 个工具节点", templateId, tools.size());
                        return tools;
                    }
                } catch (Exception e) {
                    log.warn("模板工具链解析失败: {}", templateId, e);
                }
            }
        }
        return FALLBACK_TOOLS.getOrDefault(templateId, FALLBACK_TOOLS.get("quick_selection"));
    }

    /**
     * 将某个工具节点的产物（已编辑的 outputJson）回注到 Context Bus 对应字段，
     * 供下游节点重跑时消费。
     */
    public void injectOutput(ToolContext ctx, String toolType, String outputJson) {
        if (outputJson == null) return;
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
                    // 可能为 JSON 字符串或裸文本
                    String text = outputJson.trim();
                    if (text.startsWith("\"") && text.endsWith("\"")) {
                        try { ctx.setSolutionText(objectMapper.readValue(text, String.class)); }
                        catch (Exception e) { ctx.setSolutionText(text); }
                    } else {
                        ctx.setSolutionText(text);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("产物回注失败: {} - {}", toolType, e.getMessage());
        }
    }

    /**
     * 重跑下游：从 fromOrder 之后（不含 fromOrder）的所有节点重新执行，
     * 执行前先将 fromOrder 节点（已被手动编辑）的产物回注 Context Bus，
     * 使下游基于编辑后的结果重新生成。
     *
     * @param fromOrder 被编辑节点的 toolOrder（该节点本身不重跑）
     */
    public void rerunDownstream(Long pipelineRunId, Long projectId, String templateId,
                                int fromOrder, PipelineTool.LlmConfig llmConfig) {
        PipelineRun run = pipelineRunRepository.findById(pipelineRunId)
                .orElseThrow(() -> new IllegalArgumentException("流水线不存在"));

        ToolContext parsed = parseContext(run.getContextJson());
        final ToolContext ctx = parsed != null ? parsed : ToolContext.empty();

        // 回注被编辑节点的产物
        toolExecutionRepository.findByPipelineRunIdAndToolOrder(pipelineRunId, fromOrder)
                .ifPresent(edited -> injectOutput(ctx, edited.getToolType(), edited.getOutputJson()));

        List<ToolExecution> downstream = toolExecutionRepository
                .findByPipelineRunIdOrderByToolOrder(pipelineRunId).stream()
                .filter(e -> e.getToolOrder() > fromOrder)
                .toList();

        boolean anyFailed = false;
        for (ToolExecution exec : downstream) {
            final ToolExecution fx = exec;
            PipelineTool tool = TOOL_BY_TYPE.get(fx.getToolType());
            if (tool == null) continue;
            fx.setStatus("RUNNING");
            fx.setStartedAt(Instant.now());
            fx.setLlmModel(llmConfig.model);
            toolExecutionRepository.save(fx);

            boolean success;
            try {
                success = tool.execute(ctx, llmConfig);
            } catch (Exception e) {
                log.error("重跑工具异常: {}", exec.getToolType(), e);
                success = false;
            }
            exec.setFinishedAt(Instant.now());
            exec.setStatus(success ? "SUCCESS" : "FAILED");
            exec.setOutputJson(toJson(safeOutput(ctx, exec.getToolType())));
            toolExecutionRepository.save(exec);
            if (!success) anyFailed = true;
        }

        run.setContextJson(toJson(ctx.toMap()));
        run.setFinishedAt(Instant.now());
        run.setStatus(anyFailed ? "PARTIAL" : "SUCCESS");
        pipelineRunRepository.save(run);
        log.info("下游重跑完成: runId={}, fromOrder={}, 下游节点数={}, status={}",
                pipelineRunId, fromOrder, downstream.size(), run.getStatus());
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

    /**
     * 将产物 JSON 解析为 List<T>；兼容两种编辑输入：
     * - JSON 数组（前端整列表编辑）—— 直接反序列化；
     * - 单个 JSON 对象（前端仅编辑其中一项）—— 自动包成 1 元素列表。
     * 既避免 injectOutput 因格式不符静默失败，也保证手动编辑能回注 Context Bus。
     */
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
            if (node.isTextual()) {
                // 列表型产物偶尔以纯文本给出，退化为空列表以避免打断重跑
                return List.of();
            }
        } catch (Exception e) {
            log.warn("列表型产物解析失败: {}", e.getMessage());
        }
        return List.of();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
