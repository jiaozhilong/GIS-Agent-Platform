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

    /** 最终方案文本（后续工具写入） */
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

    public static ToolContext empty() {
        ToolContext ctx = new ToolContext();
        ctx.setProductSelection(new ArrayList<>());
        return ctx;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("requirementDoc", requirementDoc);
        map.put("requirements", requirements);
        map.put("productSelection", productSelection);
        map.put("solutionText", solutionText);
        return map;
    }
}
