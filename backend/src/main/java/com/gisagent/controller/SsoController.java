package com.gisagent.controller;

import com.gisagent.config.SsoProperties;
import com.gisagent.dto.AuthDto;
import com.gisagent.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO / OIDC 登录（P7-2，通用授权码流程）。
 *
 * <p>真实身份源：端点由 {@link SsoProperties} 配置（企业微信/飞书/Google 等标准 OIDC）。
 * 沙箱无外网：开启 {@code app.sso.mock=true} 时，端点指向本服务内置 mock IdP，
 * 可端到端验证整条链路（授权→换码→取用户信息→建/关联用户→签发 JWT）。</p>
 *
 * <p>流程：前端跳转 {@code GET /api/sso/authorize} → 302 到身份源 authorize（带 state）→
 * 身份源回跳 {@code GET /api/sso/callback?code&state} → 服务端用 code 换 token →
 * 调 userInfo 取 email → 按 email 关联或新建用户 → 302 回前端 {@code ?sso_token=JWT}。</p>
 */
@RestController
@RequestMapping("/api/sso")
public class SsoController {

    private final SsoProperties sso;
    private final AuthService authService;
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper om = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    /** state -> 过期时间（防 CSRF），内存态，单实例足够 */
    private final ConcurrentHashMap<String, Long> states = new ConcurrentHashMap<>();

    public SsoController(SsoProperties sso, AuthService authService) {
        this.sso = sso;
        this.authService = authService;
    }

    private String newState() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        String s = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        states.put(s, Instant.now().plusSeconds(300).toEpochMilli());
        return s;
    }

    private boolean validState(String state) {
        if (state == null) return false;
        Long exp = states.remove(state);
        return exp != null && exp > Instant.now().toEpochMilli();
    }

    /** 发起 SSO 授权：重定向到身份源 authorize 端点（mock 模式指向内置 mock IdP） */
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(HttpServletRequest request) {
        if (!sso.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .header("X-Error", "SSO 未启用").build();
        }
        String state = newState();
        String idpAuthorize = sso.isMock()
                ? request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/api/sso/mock/authorize"
                : sso.getAuthorizeUrl();
        String url = idpAuthorize
                + "?client_id=" + (sso.getClientId() == null ? "" : sso.getClientId())
                + "&redirect_uri=" + encode(sso.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + encode(sso.getScope())
                + "&state=" + state;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    /** 前端查询 SSO 是否启用（决定是否展示登录按钮） */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of("enabled", sso.isEnabled()));
    }

    /** 身份源回调：换码 → 取用户信息 → 关联/新建用户 → 回前端带 token（mock 模式走内置 IdP） */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         HttpServletRequest request) {
        if (!validState(state)) {
            return redirectFrontend("error", "invalid_state");
        }
        String base = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        try {
            String email;
            String name;
            if (sso.isMock()) {
                // 内置 mock IdP：直接换码 + 取用户信息
                ResponseEntity<String> tokResp = rest.postForEntity(base + "/api/sso/mock/token",
                        jsonFormEntity("grant_type=authorization_code"), String.class);
                if (!tokResp.getStatusCode().is2xxSuccessful() || tokResp.getBody() == null)
                    return redirectFrontend("error", "token_failed");
                Map<?, ?> tok = om.readValue(tokResp.getBody(), Map.class);
                String accessToken = (String) tok.get("access_token");
                if (accessToken == null) return redirectFrontend("error", "no_access_token");
                ResponseEntity<String> infoResp = rest.getForEntity(
                        base + "/api/sso/mock/userinfo?access_token=" + accessToken, String.class);
                if (!infoResp.getStatusCode().is2xxSuccessful() || infoResp.getBody() == null)
                    return redirectFrontend("error", "userinfo_failed");
                Map<?, ?> info = om.readValue(infoResp.getBody(), Map.class);
                email = (String) info.get(sso.getEmailField());
                name = (String) info.get(sso.getNameField());
            } else {
                // 真实 IdP：标准授权码换码
                String tokenBody = "grant_type=authorization_code"
                        + "&code=" + code
                        + "&redirect_uri=" + sso.getRedirectUri()
                        + "&client_id=" + sso.getClientId()
                        + "&client_secret=" + sso.getClientSecret();
                ResponseEntity<String> tokResp = rest.postForEntity(sso.getTokenUrl(),
                        jsonFormEntity(tokenBody), String.class);
                if (!tokResp.getStatusCode().is2xxSuccessful() || tokResp.getBody() == null) {
                    return redirectFrontend("error", "token_failed");
                }
                Map<?, ?> tok = om.readValue(tokResp.getBody(), Map.class);
                String accessToken = (String) tok.get("access_token");
                if (accessToken == null) return redirectFrontend("error", "no_access_token");

                ResponseEntity<String> infoResp = rest.getForEntity(
                        sso.getUserInfoUrl() + "?access_token=" + accessToken, String.class);
                if (!infoResp.getStatusCode().is2xxSuccessful() || infoResp.getBody() == null) {
                    return redirectFrontend("error", "userinfo_failed");
                }
                Map<?, ?> info = om.readValue(infoResp.getBody(), Map.class);
                email = (String) info.get(sso.getEmailField());
                name = (String) info.get(sso.getNameField());
            }
            if (email == null || email.isBlank()) {
                return redirectFrontend("error", "no_email");
            }

            // 关联/新建用户并签发 JWT
            AuthDto.AuthResponse auth = authService.ssoLogin(email, name);
            return redirectFrontend("sso_token", auth.getToken());
        } catch (Exception e) {
            return redirectFrontend("error", "sso_exception");
        }
    }

    // ===== 本地 mock 身份源（仅 app.sso.mock=true 时启用，供沙箱验证）=====

    @GetMapping("/mock/authorize")
    public ResponseEntity<Void> mockAuthorize(@RequestParam("redirect_uri") String redirectUri,
                                              @RequestParam("state") String state) {
        if (!sso.isMock()) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        String url = redirectUri + "?code=mock-code&state=" + state;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    @PostMapping("/mock/token")
    public ResponseEntity<Map<String, Object>> mockToken() {
        if (!sso.isMock()) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        return ResponseEntity.ok(Map.of("access_token", "mock-access-token", "token_type", "Bearer"));
    }

    @GetMapping("/mock/userinfo")
    public ResponseEntity<Map<String, Object>> mockUserinfo() {
        if (!sso.isMock()) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        // 固定测试邮箱，便于验证"按 email 关联/新建用户"
        return ResponseEntity.ok(Map.of("email", "sso.user@example.com", "name", "SSO 测试用户"));
    }

    // ===== 工具 =====

    private ResponseEntity<Void> redirectFrontend(String key, String value) {
        String url = sso.getFrontendBase() + "?" + key + "=" + value;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
    }

    private org.springframework.http.HttpEntity<String> jsonFormEntity(String body) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
        h.setBasicAuth(sso.getClientId(), sso.getClientSecret());
        return new org.springframework.http.HttpEntity<>(body, h);
    }

    private String encode(String v) {
        try {
            return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return v;
        }
    }
}
