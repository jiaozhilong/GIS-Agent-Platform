package com.gisagent.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 文档文本提取工具。支持 PDF / Word / TXT。
 */
@Slf4j
public class FileTextExtractor {

    public static String extract(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".pdf")) {
                return extractPdf(filePath);
            } else if (name.endsWith(".docx")) {
                return extractDocx(filePath);
            } else if (name.endsWith(".txt")) {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } else {
                // 兜底：按文本读取
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("文档文本提取失败: {}", filePath, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    private static String extractPdf(String path) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private static String extractDocx(String path) throws Exception {
        try (FileInputStream fis = new FileInputStream(path);
             XWPFDocument doc = new XWPFDocument(fis)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(doc);
            return extractor.getText();
        }
    }
}
