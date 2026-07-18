package com.gisagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 保存自定义流程模板的请求体。
 * toolChain 为工具类型（toolType）有序列表，元素需为引擎已注册类型。
 */
@Data
public class TemplateSaveRequest {
    /** 模板名称（必填） */
    private String name;

    /** 模板说明 */
    private String description;

    /** 工具链：toolType 有序列表，如 ["REQUIREMENT_ANALYSIS","PRODUCT_MATCHING"]（必填，非空） */
    private List<String> toolChain;

    /** 预估耗时，如 "约 10 分钟" */
    private String estimatedTime;

    /** 可见范围：mine=仅自己，community=发布到社区市场（默认 mine） */
    private String category;
}
