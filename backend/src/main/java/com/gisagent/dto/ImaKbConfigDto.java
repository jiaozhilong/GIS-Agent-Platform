package com.gisagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class ImaKbConfigDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "知识库 ID 不能为空")
        private String kbId;

        @NotBlank(message = "知识库名称不能为空")
        private String kbName;

        private String kbType = "subscribed";

        @NotBlank(message = "知识库用途不能为空")
        private String purpose;

        @NotNull
        private Float searchWeight = 0.5f;
    }

    @Data
    public static class UpdateRequest {
        private String kbName;
        private String purpose;
        private Float searchWeight;
        private Boolean enabled;
    }

    @Data
    public static class TestResponse {
        private boolean success;
        private String message;
    }

    @Data
    public static class ConfigResponse {
        private Long id;
        private String kbId;
        private String kbName;
        private String kbType;
        private String purpose;
        private Float searchWeight;
        private Boolean enabled;
    }
}
