package com.gisagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Skill（可编排外部能力）相关的请求/响应 DTO。
 */
public class SkillDto {

    /** 创建请求 */
    @Data
    public static class CreateRequest {
        @NotBlank(message = "名称不能为空")
        private String name;

        private String description;

        /** API_ENDPOINT | GIT_REPO */
        private String type = "API_ENDPOINT";

        /** 绑定的流水线工具节点（REQUIREMENT_ANALYSIS / PPT_OUTPUT ...） */
        @NotBlank(message = "请选择绑定的工具节点")
        private String toolType;

        private String endpointUrl;
        private String apiKey;
        private String requestTemplate;
        private String gitRepoUrl;
        private String gitRef;
        private Boolean enabled = true;
    }

    /** 更新请求（所有字段可选） */
    @Data
    public static class UpdateRequest {
        private String name;
        private String description;
        private String type;
        private String toolType;
        private String endpointUrl;
        private String apiKey;
        private String requestTemplate;
        private String gitRepoUrl;
        private String gitRef;
        private Boolean enabled;
    }

    /** 响应视图（apiKey 不回显明文，仅告知是否已配置） */
    @Data
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private String type;
        private String toolType;
        private String endpointUrl;
        private String requestTemplate;
        private String gitRepoUrl;
        private String gitRef;
        private Boolean enabled;
        private Boolean hasApiKey;
    }
}
