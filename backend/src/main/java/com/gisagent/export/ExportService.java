package com.gisagent.export;

import com.gisagent.pipeline.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 方案文档导出服务。
 * 支持生成 Word .docx 和 Markdown 两种格式。
 */
@Service
@Slf4j
public class ExportService {

    @Value("${storage.export-dir:./data/exports}")
    private String exportDir;

    public String exportMarkdown(Long projectId, String projectName, ToolContext context) {
        ensureDir();
        String fileName = String.format("%d_solution_%s.md", projectId,
                timestamp());
        Path path = Path.of(exportDir, fileName);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(projectName != null ? projectName : "GIS 解决方案").append("\n\n");
        sb.append("> 生成时间：").append(now()).append("\n\n");

        // 需求分析
        if (context.getRequirements() != null) {
            sb.append("## 一、需求分析\n\n");
            appendList(sb, "### 功能需求", context.getRequirements().getFunctional());
            appendList(sb, "### 非功能需求", context.getRequirements().getNonFunctional());
            appendList(sb, "### 约束条件", context.getRequirements().getConstraints());
            sb.append("### 行业场景\n\n").append(context.getRequirements().getIndustry()).append("\n\n");
        }

        // 产品选型
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
            // 标题
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText(projectName != null ? projectName : "GIS 解决方案");
            titleRun.setBold(true);
            titleRun.setFontSize(20);

            // 副标题
            XWPFParagraph sub = doc.createParagraph();
            sub.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subRun = sub.createRun();
            subRun.setText("生成时间：" + now());
            subRun.setFontSize(10);
            subRun.setColor("808080");

            // 需求分析
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

            // 产品选型
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
            XWPFRun r = p.createRun();
            r.setText("• " + (item == null ? "" : item));
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
