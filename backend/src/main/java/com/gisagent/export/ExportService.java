package com.gisagent.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.entity.PptTemplate;
import com.gisagent.pipeline.ToolContext;
import com.gisagent.repository.PptTemplateRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 方案文档导出服务。
 * 支持生成 Word .docx、Markdown 与 PowerPoint .pptx 三种格式。
 *
 * Word / PPT 通过 Node.js 脚本（backend/scripts）生成：
 *  - generate-docx.js：标书正式风格（黑体标题 + 宋体正文 + 自动目录 + 页眉页脚）
 *  - generate-pptx.js：深蓝科技风（深蓝黑底 + 青/蓝绿主色），可选品牌模板
 * Java 侧仅负责拼装数据、调用脚本、回收临时文件，避免 POI 文档生成的大内存占用。
 */
@Service
@Slf4j
public class ExportService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${storage.export-dir:./data/exports}")
    private String exportDir;

    /** 可选 PPT 品牌模板路径（由 UI 提供 .pptx，JIT 门禁产物） */
    @Value("${storage.pptx-template:}")
    private String pptxTemplatePath;

    private final PptTemplateRepository pptTemplateRepository;

    public ExportService(PptTemplateRepository pptTemplateRepository) {
        this.pptTemplateRepository = pptTemplateRepository;
    }

    @PostConstruct
    public void init() {
        // 解析为绝对路径，避免相对路径依赖 JVM 工作目录导致导出位置不可预测。
        this.exportDir = new File(exportDir).getAbsolutePath();
    }

    public String exportMarkdown(Long projectId, String projectName, ToolContext context) {
        ensureDir();
        String fileName = String.format("%d_solution_%s.md", projectId, timestamp());
        Path path = Path.of(exportDir, fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectName != null ? projectName : "GIS 解决方案").append("\n\n");
        sb.append("> 生成时间：").append(now()).append("\n\n");

        if (context.getRequirements() != null) {
            sb.append("## 一、需求分析\n\n");
            appendList(sb, "### 功能需求", context.getRequirements().getFunctional());
            appendList(sb, "### 非功能需求", context.getRequirements().getNonFunctional());
            appendList(sb, "### 约束条件", context.getRequirements().getConstraints());
            sb.append("### 行业场景\n\n").append(context.getRequirements().getIndustry()).append("\n\n");
        }

        if (context.getProductSelection() != null && !context.getProductSelection().isEmpty()) {
            sb.append("## 二、产品选型清单\n\n");
            sb.append("| 产品名称 | 版本 | 匹配理由 | 需求覆盖率 |\n");
            sb.append("|---------|------|---------|-----------|\n");
            for (ToolContext.ProductSelection p : context.getProductSelection()) {
                sb.append(String.format("| %s | %s | %s | %s |\n",
                        p.getProductName(), p.getVersion(), p.getReason(), p.getCoverage()));
            }
            sb.append("\n");
        }

        if (context.getCaseRecommendations() != null && !context.getCaseRecommendations().isEmpty()) {
            sb.append("## 三、参考案例\n\n");
            for (ToolContext.CaseRecommendation c : context.getCaseRecommendations()) {
                sb.append("- **").append(c.getCaseName()).append("**（").append(c.getScenario()).append("）\n");
                sb.append("  - 使用产品：").append(c.getProductsUsed()).append("\n");
                sb.append("  - 成效：").append(c.getKeyEffect()).append("\n");
                sb.append("  - 匹配点：").append(c.getMatchReason()).append("\n\n");
            }
        }

        if (context.getCompetitorAnalysis() != null && !context.getCompetitorAnalysis().isEmpty()) {
            sb.append("## 四、竞品对比\n\n");
            for (ToolContext.CompetitorComparison c : context.getCompetitorAnalysis()) {
                sb.append("- **").append(c.getCompetitorName()).append("**\n");
                sb.append("  - 我方优势：").append(c.getOurAdvantage()).append("\n");
                sb.append("  - 相对劣势：").append(c.getOurDisadvantage()).append("\n");
                sb.append("  - 应对建议：").append(c.getRecommendation()).append("\n\n");
            }
        }

        if (context.getArchitectureDiagram() != null) {
            sb.append("## 五、技术架构\n\n");
            sb.append("**").append(context.getArchitectureDiagram().getTitle()).append("**\n\n");
            sb.append(context.getArchitectureDiagram().getDescription()).append("\n\n");
            sb.append("```mermaid\n").append(context.getArchitectureDiagram().getMermaid()).append("\n```\n\n");
        }

        if (context.getSolutionOutline() != null) {
            sb.append("## 六、方案大纲\n\n");
            sb.append(context.getSolutionOutline().getOverview()).append("\n\n");
            if (context.getSolutionOutline().getSections() != null) {
                for (ToolContext.OutlineSection s : context.getSolutionOutline().getSections()) {
                    sb.append("### ").append(s.getTitle()).append("\n\n").append(s.getKeyPoints()).append("\n\n");
                }
            }
        }

        if (context.getQualityCheck() != null) {
            sb.append("## 七、方案质检\n\n");
            sb.append("整体评分：**").append(context.getQualityCheck().getOverallScore()).append("**\n\n");
            if (context.getQualityCheck().getDimensions() != null) {
                for (ToolContext.DimensionScore d : context.getQualityCheck().getDimensions()) {
                    sb.append("- ").append(d.getDimension()).append("：").append(d.getScore())
                            .append("（").append(d.getComment()).append("）\n");
                }
            }
            if (context.getQualityCheck().getSuggestions() != null) {
                sb.append("\n改进建议：\n");
                for (String s : context.getQualityCheck().getSuggestions()) {
                    sb.append("- ").append(s).append("\n");
                }
            }
            sb.append("\n");
        }

        if (context.getSolutionText() != null && !context.getSolutionText().isBlank()) {
            sb.append("## 八、解决方案正文\n\n").append(context.getSolutionText()).append("\n");
        }

        try {
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
            log.info("Markdown 导出成功：{}", path);
            return path.toString();
        } catch (Exception e) {
            log.error("Markdown 导出失败", e);
            throw new RuntimeException("Markdown 导出失败", e);
        }
    }

    public String exportDocx(Long projectId, String projectName, ToolContext context) {
        ensureDir();
        String fileName = String.format("%d_solution_%s.docx", projectId, timestamp());
        Path path = Path.of(exportDir, fileName);
        Map<String, Object> data = new HashMap<>(context.toMap());
        data.put("projectName", projectName);
        boolean ok = runNodeScript("generate-docx.js", data, path, null);
        if (ok) return path.toString();
        throw new RuntimeException("DOCX 生成失败（Node 脚本执行异常，请确认 backend 已执行 npm install 且 node 可用）");
    }

    public String exportPptx(Long projectId, String projectName, ToolContext context) {
        return exportPptx(projectId, projectName, context, null, null);
    }

    /**
     * 导出 PPT，支持指定模板。
     * @param templateId 模板 ID，为 null 时使用用户默认模板或内置风格
     * @param userId 模板所属用户，为 null 时仅使用全局配置的模板路径
     */
    public String exportPptx(Long projectId, String projectName, ToolContext context, Long templateId, Long userId) {
        ensureDir();
        String fileName = String.format("%d_solution_%s.pptx", projectId, timestamp());
        Path path = Path.of(exportDir, fileName);
        Map<String, Object> data = new HashMap<>(context.toMap());
        data.put("projectName", projectName);
        String tpl = resolvePptxTemplate(templateId, userId);
        boolean ok = runNodeScript("generate-pptx.js", data, path, tpl);
        if (ok) return path.toString();
        throw new RuntimeException("PPTX 生成失败（Node 脚本执行异常，请确认 backend 已执行 npm install 且 node 可用）");
    }

    /** 解析 PPT 模板：优先按 templateId 查找，其次用户默认模板，再次全局配置，最后回退内置风格。 */
    private String resolvePptxTemplate(Long templateId, Long userId) {
        // 1) 指定了模板 ID → 直接查找
        if (templateId != null && userId != null) {
            Optional<PptTemplate> opt = pptTemplateRepository.findByIdAndUserId(templateId, userId);
            if (opt.isPresent() && Files.exists(Path.of(opt.get().getFilePath()))) {
                return opt.get().getFilePath();
            }
        }
        // 2) 用户默认模板
        if (userId != null) {
            Optional<PptTemplate> opt = pptTemplateRepository.findByUserIdAndIsDefaultTrue(userId);
            if (opt.isPresent() && Files.exists(Path.of(opt.get().getFilePath()))) {
                return opt.get().getFilePath();
            }
            // 3) 用户任意一个模板
            List<com.gisagent.entity.PptTemplate> all = pptTemplateRepository.findByUserIdOrderByCreatedAtDesc(userId);
            for (PptTemplate t : all) {
                if (Files.exists(Path.of(t.getFilePath()))) {
                    return t.getFilePath();
                }
            }
        }
        // 4) 全局配置路径
        if (pptxTemplatePath != null && !pptxTemplatePath.isBlank() && Files.exists(Path.of(pptxTemplatePath))) {
            return pptxTemplatePath;
        }
        // 5) 默认目录
        String defaultPath = "./data/templates/brand-template.pptx";
        if (Files.exists(Path.of(defaultPath))) {
            return defaultPath;
        }
        // 6) 无模板，使用脚本内置深蓝风格
        return null;
    }

    /**
     * 调用 backend/scripts 下的 Node 脚本生成文档。
     * 约定：node <script> <input.json> <output> [template]
     * 脚本成功退出码为 0 且产出文件存在。
     */
    private boolean runNodeScript(String scriptName, Map<String, Object> data, Path outputPath, String templatePath) {
        try {
            Path inputJson = Files.createTempFile("export-", ".json");
            JSON.writeValue(inputJson.toFile(), data);

            List<String> cmd = new ArrayList<>();
            cmd.add(resolveNodeBin());
            cmd.add(scriptDir().resolve(scriptName).toString());
            cmd.add(inputJson.toString());
            cmd.add(outputPath.toString());
            if (templatePath != null) {
                cmd.add(templatePath);
            }

            log.info("执行文档生成脚本：{}", String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.directory(new File("."));
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[node:{}] {}", scriptName, line);
                }
            }
            int code = p.waitFor();
            Files.deleteIfExists(inputJson);
            if (code != 0) {
                log.error("Node 脚本 {} 退出码 {}（非零）", scriptName, code);
                return false;
            }
            boolean exists = Files.exists(outputPath);
            if (!exists) {
                log.error("Node 脚本 {} 执行成功但未找到产出文件：{}", scriptName, outputPath);
            }
            return exists;
        } catch (Exception e) {
            log.error("执行 Node 脚本失败：{}", scriptName, e);
            return false;
        }
    }

    /** 解析 node 可执行文件：优先环境变量 NODE_BIN，否则走系统 PATH。 */
    private String resolveNodeBin() {
        String node = System.getenv("NODE_BIN");
        if (node != null && !node.isBlank()) {
            return node;
        }
        return "node";
    }

    /** 定位脚本目录：优先 classpath 相对 ./scripts，回退到工作目录的 scripts。 */
    private Path scriptDir() {
        Path p = Path.of("./scripts");
        if (Files.exists(p)) {
            return p;
        }
        return Path.of(System.getProperty("user.dir"), "scripts");
    }

    private void appendList(StringBuilder sb, String heading, List<String> items) {
        if (items == null || items.isEmpty()) return;
        sb.append(heading).append("\n\n");
        for (String item : items) {
            sb.append("- ").append(item).append("\n");
        }
        sb.append("\n");
    }

    private void ensureDir() {
        new File(exportDir).mkdirs();
    }

    private String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
