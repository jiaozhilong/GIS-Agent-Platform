package com.gisagent.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线共享上下文（Context Bus）。
 * 所有工具节点通过此对象传递数据，而非点对点耦合。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolContext {

    /** 原始需求文档文本 */
    private String requirementDoc;

    /** Tool-1 写入：结构化需求清单 */
    private RequirementResult requirements;

    /** Tool-3 写入：产品选型清单 */
    private List<ProductSelection> productSelection;

    /** Tool-2 写入：案例推荐清单 */
    private List<CaseRecommendation> caseRecommendations;

    /** Tool-4 写入：竞品对比分析 */
    private List<CompetitorComparison> competitorAnalysis;

    /** Tool-5 写入：架构图（含 Mermaid 源码） */
    private ArchitectureDiagram architectureDiagram;

    /** Tool-6 写入：方案框架大纲 */
    private SolutionOutline solutionOutline;

    /** Tool-7 写入：方案质检结果 */
    private QualityCheck qualityCheck;

    /** 最终方案文本（Tool-8 写入） */
    private String solutionText;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequirementResult {
        private List<String> functional;
        private List<String> nonFunctional;
        private List<String> constraints;
        private String industry;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductSelection {
        private String productName;
        private String version;
        private String reason;
        private String coverage;
    }

    /** Tool-2 输出：单个推荐案例 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CaseRecommendation {
        private String caseName;
        private String scenario;
        private String productsUsed;
        private String keyEffect;
        private String matchReason;
    }

    /** Tool-4 输出：单条竞品对比 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CompetitorComparison {
        private String competitorName;
        private String ourAdvantage;
        private String ourDisadvantage;
        private String recommendation;
    }

    /** Tool-5 输出：架构图（Mermaid 源码 + 说明） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ArchitectureDiagram {
        private String title;
        private String mermaid;
        private String description;
    }

    /** Tool-6 输出：方案大纲 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SolutionOutline {
        private String overview;
        private List<OutlineSection> sections;
    }

    /** Tool-6 输出：大纲章节 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutlineSection {
        private String title;
        private String keyPoints;
    }

    /** Tool-7 输出：质检结果 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QualityCheck {
        private double overallScore;
        private List<DimensionScore> dimensions;
        private List<String> suggestions;
    }

    /** Tool-7 输出：单维度评分 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DimensionScore {
        private String dimension;
        private double score;
        private String comment;
    }

    public static ToolContext empty() {
        ToolContext ctx = new ToolContext();
        ctx.setProductSelection(new ArrayList<>());
        ctx.setCaseRecommendations(new ArrayList<>());
        ctx.setCompetitorAnalysis(new ArrayList<>());
        return ctx;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("requirementDoc", requirementDoc);
        map.put("requirements", requirements);
        map.put("productSelection", productSelection);
        map.put("caseRecommendations", caseRecommendations);
        map.put("competitorAnalysis", competitorAnalysis);
        map.put("architectureDiagram", architectureDiagram);
        map.put("solutionOutline", solutionOutline);
        map.put("qualityCheck", qualityCheck);
        map.put("solutionText", solutionText);
        return map;
    }
}
