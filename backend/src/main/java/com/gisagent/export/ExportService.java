package com.gisagent.export;

import com.gisagent.pipeline.ToolContext;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 方案文档导出服务。
 * 支持生成 Word .docx、Markdown 与 PowerPoint .pptx 三种格式。
 * PPTX 优先使用配置的ike品牌模板（storage.pptx-template），缺失时回退默认深蓝/青色主题。
 */
@Service
@Slf4j
public class ExportService {

    @Value("${storage.export-dir:./data/exports}")
    private String exportDir;

    /** 可选 PPT 品牌模板路径（由 UI 提供 .pptx，JIT 门禁产物） */
    @Value("${storage.pptx-template:}")
    private String pptxTemplatePath;

    @PostConstruct
    public void init() {
        // 解析为绝对路径，避免相对路径依赖 JVM 工作目录导致导出位置不可预测。
        this.exportDir = new File(exportDir).getAbsolutePath();
    }

    // 品牌色：深蓝黑底 + 青/蓝绿主色
    private static final byte[] BG = {0x0A, 0x1A, 0x2F};
    private static final byte[] ACCENT = {0x00, (byte) 0xE5, (byte) 0xD4};

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

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText(projectName != null ? projectName : "GIS 解决方案");
            titleRun.setBold(true);
            titleRun.setFontSize(20);

            XWPFParagraph sub = doc.createParagraph();
            sub.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subRun = sub.createRun();
            subRun.setText("生成时间：" + now());
            subRun.setFontSize(10);
            subRun.setColor("808080");

            if (context.getRequirements() != null) {
                addHeading(doc, "一、需求分析");
                addSubHeading(doc, "功能需求");
                addBullets(doc, context.getRequirements().getFunctional());
                addSubHeading(doc, "非功能需求");
                addBullets(doc, context.getRequirements().getNonFunctional());
                addSubHeading(doc, "约束条件");
                addBullets(doc, context.getRequirements().getConstraints());
                addSubHeading(doc, "行业场景");
                addParagraph(doc, context.getRequirements().getIndustry());
            }

            if (context.getProductSelection() != null && !context.getProductSelection().isEmpty()) {
                addHeading(doc, "二、产品选型清单");
                XWPFTable table = doc.createTable(context.getProductSelection().size() + 1, 4);
                setCell(table.getRow(0).getCell(0), "产品名称");
                setCell(table.getRow(0).getCell(1), "版本");
                setCell(table.getRow(0).getCell(2), "匹配理由");
                setCell(table.getRow(0).getCell(3), "需求覆盖率");
                for (int i = 0; i < context.getProductSelection().size(); i++) {
                    ToolContext.ProductSelection p = context.getProductSelection().get(i);
                    XWPFTableRow row = table.getRow(i + 1);
                    setCell(row.getCell(0), p.getProductName());
                    setCell(row.getCell(1), p.getVersion());
                    setCell(row.getCell(2), p.getReason());
                    setCell(row.getCell(3), p.getCoverage());
                }
            }

            if (context.getCaseRecommendations() != null && !context.getCaseRecommendations().isEmpty()) {
                addHeading(doc, "三、参考案例");
                for (ToolContext.CaseRecommendation c : context.getCaseRecommendations()) {
                    addSubHeading(doc, c.getCaseName() + "（" + c.getScenario() + "）");
                    addParagraph(doc, "使用产品：" + c.getProductsUsed());
                    addParagraph(doc, "成效：" + c.getKeyEffect());
                    addParagraph(doc, "匹配点：" + c.getMatchReason());
                }
            }

            if (context.getCompetitorAnalysis() != null && !context.getCompetitorAnalysis().isEmpty()) {
                addHeading(doc, "四、竞品对比");
                for (ToolContext.CompetitorComparison c : context.getCompetitorAnalysis()) {
                    addSubHeading(doc, c.getCompetitorName());
                    addParagraph(doc, "我方优势：" + c.getOurAdvantage());
                    addParagraph(doc, "相对劣势：" + c.getOurDisadvantage());
                    addParagraph(doc, "应对建议：" + c.getRecommendation());
                }
            }

            if (context.getArchitectureDiagram() != null) {
                addHeading(doc, "五、技术架构");
                addSubHeading(doc, context.getArchitectureDiagram().getTitle());
                addParagraph(doc, context.getArchitectureDiagram().getDescription());
                addParagraph(doc, "Mermaid 源码：\n" + context.getArchitectureDiagram().getMermaid());
            }

            if (context.getSolutionOutline() != null) {
                addHeading(doc, "六、方案大纲");
                addParagraph(doc, context.getSolutionOutline().getOverview());
                if (context.getSolutionOutline().getSections() != null) {
                    for (ToolContext.OutlineSection s : context.getSolutionOutline().getSections()) {
                        addSubHeading(doc, s.getTitle());
                        addParagraph(doc, s.getKeyPoints());
                    }
                }
            }

            if (context.getQualityCheck() != null) {
                addHeading(doc, "七、方案质检");
                addParagraph(doc, "整体评分：" + context.getQualityCheck().getOverallScore());
                if (context.getQualityCheck().getDimensions() != null) {
                    for (ToolContext.DimensionScore d : context.getQualityCheck().getDimensions()) {
                        addParagraph(doc, d.getDimension() + "：" + d.getScore() + "（" + d.getComment() + "）");
                    }
                }
                if (context.getQualityCheck().getSuggestions() != null) {
                    addSubHeading(doc, "改进建议");
                    addBullets(doc, context.getQualityCheck().getSuggestions());
                }
            }

            if (context.getSolutionText() != null && !context.getSolutionText().isBlank()) {
                addHeading(doc, "八、解决方案正文");
                addParagraph(doc, context.getSolutionText());
            }

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                doc.write(out);
            }
            log.info("DOCX 导出成功：{}", path);
            return path.toString();
        } catch (Exception e) {
            log.error("DOCX 导出失败", e);
            throw new RuntimeException("DOCX 导出失败", e);
        }
    }

    public String exportPptx(Long projectId, String projectName, ToolContext context) {
        ensureDir();
        String fileName = String.format("%d_solution_%s.pptx", projectId, timestamp());
        Path path = Path.of(exportDir, fileName);

        XMLSlideShow ppt = loadPptxTemplate();
        try {
            // 封面
            XSLFSlide cover = addSlide(ppt);
            addTitle(cover, projectName != null ? projectName : "GIS 解决方案", 60, 260, 40, 54);
            addText(cover, "生成时间：" + now(), 60, 330, 20, false, "B0BEC5");

            if (context.getRequirements() != null) {
                List<String> req = new ArrayList<>();
                addIfNotNull(req, "功能需求：", context.getRequirements().getFunctional());
                addIfNotNull(req, "非功能需求：", context.getRequirements().getNonFunctional());
                addIfNotNull(req, "约束条件：", context.getRequirements().getConstraints());
                if (context.getRequirements().getIndustry() != null) {
                    req.add("行业场景：" + context.getRequirements().getIndustry());
                }
                addBulletSlide(ppt, "需求分析", req);
            }

            if (context.getProductSelection() != null && !context.getProductSelection().isEmpty()) {
                List<String> prod = new ArrayList<>();
                for (ToolContext.ProductSelection p : context.getProductSelection()) {
                    prod.add(p.getProductName() + " " + p.getVersion() + " —— " + p.getReason());
                }
                addBulletSlide(ppt, "产品选型", prod);
            }

            if (context.getCaseRecommendations() != null && !context.getCaseRecommendations().isEmpty()) {
                List<String> cases = new ArrayList<>();
                for (ToolContext.CaseRecommendation c : context.getCaseRecommendations()) {
                    cases.add(c.getCaseName() + "（" + c.getScenario() + "）：" + c.getKeyEffect());
                }
                addBulletSlide(ppt, "参考案例", cases);
            }

            if (context.getCompetitorAnalysis() != null && !context.getCompetitorAnalysis().isEmpty()) {
                List<String> comp = new ArrayList<>();
                for (ToolContext.CompetitorComparison c : context.getCompetitorAnalysis()) {
                    comp.add(c.getCompetitorName() + "：优势 " + c.getOurAdvantage()
                            + "；应对 " + c.getRecommendation());
                }
                addBulletSlide(ppt, "竞品对比", comp);
            }

            if (context.getArchitectureDiagram() != null) {
                List<String> arch = new ArrayList<>();
                arch.add(context.getArchitectureDiagram().getDescription());
                arch.add("（架构图 Mermaid 源码详见 Word / Markdown 导出）");
                addBulletSlide(ppt, "技术架构 · " + context.getArchitectureDiagram().getTitle(), arch);
            }

            if (context.getQualityCheck() != null) {
                List<String> qc = new ArrayList<>();
                qc.add("整体评分：" + context.getQualityCheck().getOverallScore());
                if (context.getQualityCheck().getDimensions() != null) {
                    for (ToolContext.DimensionScore d : context.getQualityCheck().getDimensions()) {
                        qc.add(d.getDimension() + "：" + d.getScore());
                    }
                }
                addBulletSlide(ppt, "方案质检", qc);
            }

            if (context.getSolutionText() != null && !context.getSolutionText().isBlank()) {
                // 方案正文较长，截断到一页可读范围（完整版见 docx/md）
                String text = context.getSolutionText();
                if (text.length() > 1200) text = text.substring(0, 1200) + "\n……（完整内容见 Word / Markdown 导出）";
                addBulletSlide(ppt, "解决方案正文（摘要）", List.of(text));
            }

            try (FileOutputStream out = new FileOutputStream(path.toFile())) {
                ppt.write(out);
            }
            ppt.close();
            log.info("PPTX 导出成功：{}", path);
            return path.toString();
        } catch (Exception e) {
            log.error("PPTX 导出失败", e);
            throw new RuntimeException("PPTX 导出失败", e);
        }
    }

    // ---- PPTX 辅助 ----

    private XMLSlideShow loadPptxTemplate() {
        if (pptxTemplatePath != null && !pptxTemplatePath.isBlank()
                && Files.exists(Path.of(pptxTemplatePath))) {
            try {
                XMLSlideShow tpl = new XMLSlideShow(new FileInputStream(pptxTemplatePath));
                log.info("使用 PPT 品牌模板：{}", pptxTemplatePath);
                return tpl;
            } catch (Exception e) {
                log.warn("加载 PPT 模板失败，回退默认布局", e);
            }
        }
        log.warn("未配置 PPT 品牌模板（storage.pptx-template），使用默认深蓝/青色布局；建议由 UI 提供 .pptx 模板");
        return new XMLSlideShow();
    }

    private XSLFSlide addSlide(XMLSlideShow ppt) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(new Color(BG[0] & 0xFF, BG[1] & 0xFF, BG[2] & 0xFF));
        return slide;
    }

    private void addTitle(XSLFSlide slide, String text, int x, int y, int w, int size) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, 80));
        XSLFTextParagraph p = box.addNewTextParagraph();
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text);
        r.setFontColor(new Color(ACCENT[0] & 0xFF, ACCENT[1] & 0xFF, ACCENT[2] & 0xFF));
        r.setBold(true);
        r.setFontSize((double) size);
    }

    private void addText(XSLFSlide slide, String text, int x, int y, int size, boolean bold, String hex) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, 900, 40));
        XSLFTextParagraph p = box.addNewTextParagraph();
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text);
        r.setFontSize((double) size);
        r.setBold(bold);
        r.setFontColor(Color.decode("#" + hex));
    }

    private void addBulletSlide(XMLSlideShow ppt, String title, List<String> items) {
        XSLFSlide slide = addSlide(ppt);
        addTitle(slide, title, 60, 40, 900, 32);
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(60, 110, 900, 480));
        for (String item : items) {
            XSLFTextParagraph p = box.addNewTextParagraph();
            p.setBullet(true);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(item);
            r.setFontColor(Color.white);
            r.setFontSize(16.0);
        }
    }

    private void addIfNotNull(List<String> target, String prefix, List<String> items) {
        if (items == null) return;
        for (String it : items) target.add(prefix + it);
    }

    // ---- DOCX 辅助 ----

    private void addHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(16);
        r.addBreak();
    }

    private void addSubHeading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(true);
        r.setFontSize(13);
    }

    private void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().setText(text == null ? "" : text);
    }

    private void addBullets(XWPFDocument doc, List<String> items) {
        if (items == null) return;
        for (String item : items) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("• " + (item == null ? "" : item));
        }
    }

    private void setCell(XWPFTableCell cell, String text) {
        cell.setText(text == null ? "" : text);
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
