package com.gisagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthDto {

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度 3-64 字符")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 128, message = "密码长度 6-128 字符")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class AuthResponse {
        private String token;
        private String username;
        private Long userId;

        public AuthResponse(String token, String username, Long userId) {
            this.token = token;
            this.username = username;
            this.userId = userId;
        }
    }
}
