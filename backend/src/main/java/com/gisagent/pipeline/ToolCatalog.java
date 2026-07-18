package com.gisagent.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平台可编排工具目录（单一事实来源），被 Skills 清单接口与 Agent 自编排共用。
 * 新增/移除工具后在此与对应 PipelineTool bean 同步即可。
 */
@Component
public class ToolCatalog {

    public static class Meta {
        public final String name;
        public final String description;
        public final String category;

        public Meta(String name, String description, String category) {
            this.name = name;
            this.description = description;
            this.category = category;
        }
    }

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

    /** 按目录顺序返回已注册工具类型（含目录未覆盖的兜底项） */
    public List<String> orderedTypes(List<String> registeredTypes) {
        List<String> out = new ArrayList<>();
        for (String k : CATALOG.keySet()) {
            if (registeredTypes.contains(k)) out.add(k);
        }
        for (String t : registeredTypes) {
            if (!CATALOG.containsKey(t)) out.add(t);
        }
        return out;
    }

    /** 生成 LLM 提示词中的工具清单（编号 + [TOOL_TYPE] + 名称 + 描述） */
    public String toPrompt(List<String> registeredTypes) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (String t : orderedTypes(registeredTypes)) {
            Meta m = CATALOG.get(t);
            String name = m != null ? m.name : t;
            String desc = m != null ? m.description : "自定义工具";
            sb.append(i++).append(". [").append(t).append("] ").append(name).append("：").append(desc).append("\n");
        }
        return sb.toString();
    }

    /** 生成 Skills 清单接口数据（与历史结构一致） */
    public List<Map<String, Object>> toSkills(List<String> registeredTypes) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String t : orderedTypes(registeredTypes)) {
            Meta m = CATALOG.get(t);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("toolType", t);
            item.put("name", m != null ? m.name : t);
            item.put("description", m != null ? m.description : "");
            item.put("category", m != null ? m.category : "工具");
            list.add(item);
        }
        return list;
    }

    public String nameOf(String toolType) {
        Meta m = CATALOG.get(toolType);
        return m != null ? m.name : toolType;
    }
}
