package com.gisagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class ProjectDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "项目名称不能为空")
        private String name;

        private String description;

        @NotBlank(message = "模板不能为空")
        private String templateId;
    }

    @Data
    public static class ProjectResponse {
        private Long id;
        private String name;
        private String description;
        private String templateId;
        private String status;
        private String createdAt;
    }

    @Data
    public static class PipelineStartResponse {
        private Long pipelineRunId;
        private Long projectId;
        private String status;
    }

    @Data
    public static class ToolStatusDto {
        private String toolType;
        private Integer toolOrder;
        private String status;
        private Object output;
        private String errorMessage;
    }

    @Data
    public static class PipelineStatusDto {
        private Long pipelineRunId;
        private Long projectId;
        private String status;
        private java.util.List<ToolStatusDto> tools;
        private Object context;
    }
}
