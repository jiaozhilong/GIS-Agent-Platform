package com.gisagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** SSO / OIDC 配置（通用授权码流程，支持企业微信/飞书/Google 等标准 OIDC 身份源）。 */
@Configuration
@ConfigurationProperties(prefix = "app.sso")
@Getter
@Setter
public class SsoProperties {
    /** 是否启用 SSO 登录入口 */
    private boolean enabled = false;

    /** 本地 mock 身份源（沙箱验证用，生产务必关闭） */
    private boolean mock = false;

    private String clientId = "";
    private String clientSecret = "";

    /** 身份源各端点；mock 模式下指向本服务内置 mock IdP */
    private String authorizeUrl = "";
    private String tokenUrl = "";
    private String userInfoUrl = "";

    /** 回调地址（需与身份源后台配置一致） */
    private String redirectUri = "http://localhost:8088/api/sso/callback";

    /** 申请的 scope，默认 openid email profile */
    private String scope = "openid email profile";

    /** 登录成功后前端回跳地址（token 以 ?sso_token= 形式带回） */
    private String frontendBase = "http://localhost:5173/login";

    /** userInfo 响应中提取邮箱/姓名的字段名 */
    private String emailField = "email";
    private String nameField = "name";
}
