package com.gisagent.controller;

import com.gisagent.pipeline.PipelineTool;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台可编排 Skills（流水线工具）能力清单接口。
 * 数量与内容由实际注册到 Spring 容器的 PipelineTool bean 驱动，
 * 而非写死常量；新增/移除工具后此处自动同步。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final List<PipelineTool> tools;

    public SkillsController(List<PipelineTool> tools) {
        this.tools = tools;
    }

    /** toolType -> 展示元数据（按流水线执行顺序排列） */
    private static final LinkedHashMap<String, Meta> CATALOG = new LinkedHashMap<>();

    static {
        CATALOG.put("REQUIREMENT_ANALYSIS", new Meta("需求分析", "解析原始需求文档，输出结构化功能/非功能/约束清单", "输入解析"));
        CATALOG.put("PRODUCT_MATCHING", new Meta("产品选型", "基于需求匹配 SuperMap 等产品组合并给出覆盖度", "方案生成"));
        CATALOG.put("CASE_RECOMMEND", new Meta("案例推荐", "从知识库检索相似行业案例并给出匹配度", "知识检索"));
        CATALOG.put("COMPETITOR_ANALYSIS", new Meta("竞品对比", "对比竞品优劣势并输出优势信心分", "知识检索"));
        CATALOG.put("ARCHITECTURE_DIAGRAM", new Meta("架构图", "生成方案技术架构 Mermaid 图与说明", "方案生成"));
        CATALOG.put("SOLUTION_OUTLINE", new Meta("方案大纲", "输出方案整体框架与章节要点", "方案生成"));
        CATALOG.put("SOLUTION_QC", new Meta("方案质检", "多维度质检评分并判定是否通过", "质量保障"));
        CATALOG.put("SOLUTION_OUTPUT", new Meta("方案输出", "汇总生成最终方案文本并支持导出", "交付"));
    }

    @GetMapping
    public Map<String, Object> list() {
        Set<String> registered = new LinkedHashSet<>();
        for (PipelineTool t : tools) {
            registered.add(t.getToolType());
        }

        List<Map<String, Object>> skills = new ArrayList<>();
        // 先按目录顺序放入已注册的工具（保证稳定、可读的顺序）
        for (Map.Entry<String, Meta> e : CATALOG.entrySet()) {
            if (registered.contains(e.getKey())) {
                skills.add(toItem(e.getKey(), e.getValue()));
            }
        }
        // 再补充目录中未覆盖、但实际已注册的工具（兜底，避免遗漏）
        for (PipelineTool t : tools) {
            if (!CATALOG.containsKey(t.getToolType())) {
                skills.add(toItem(t.getToolType(), new Meta(t.getToolType(), "", "工具")));
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", skills.size());
        resp.put("skills", skills);
        return resp;
    }

    private static Map<String, Object> toItem(String toolType, Meta meta) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("toolType", toolType);
        item.put("name", meta.name);
        item.put("description", meta.description);
        item.put("category", meta.category);
        return item;
    }

    private static class Meta {
        final String name;
        final String description;
        final String category;

        Meta(String name, String description, String category) {
            this.name = name;
            this.description = description;
            this.category = category;
        }
    }
}
