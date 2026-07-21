package com.gisagent.controller;

import com.gisagent.dto.ImaKbConfigDto;
import com.gisagent.entity.ImaCredential;
import com.gisagent.repository.ImaCredentialRepository;
import com.gisagent.service.ImaSearchService;
import com.gisagent.util.EncryptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;

@RestController
@RequestMapping("/api/ima/credentials")
public class ImaCredentialController {

    private final ImaCredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final ImaSearchService imaSearchService;
    private final String defaultBaseUrl;

    public ImaCredentialController(ImaCredentialRepository credentialRepository,
                                    EncryptionService encryptionService,
                                    ImaSearchService imaSearchService,
                                    @Value("${ima.openapi-base-url:https://ima.qq.com/openapi/wiki/v1}") String defaultBaseUrl) {
        this.credentialRepository = credentialRepository;
        this.encryptionService = encryptionService;
        this.imaSearchService = imaSearchService;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    /** 读取本用户的 IMA 凭证状态（脱敏，不回显明文） */
    @GetMapping
    public ResponseEntity<ImaKbConfigDto.CredentialResponse> get(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        ImaKbConfigDto.CredentialResponse resp = new ImaKbConfigDto.CredentialResponse();
        Optional<ImaCredential> opt = credentialRepository.findByUserId(userId);
        if (opt.isPresent()) {
            ImaCredential c = opt.get();
            resp.setConfigured(true);
            resp.setClientIdMasked(mask(c.getClientIdEncrypted() != null ? encryptionService.decrypt(c.getClientIdEncrypted()) : null));
            resp.setBaseUrl(c.getBaseUrl() != null && !c.getBaseUrl().isBlank() ? c.getBaseUrl() : defaultBaseUrl);
            resp.setMessage("已配置");
        } else {
            resp.setConfigured(false);
            resp.setBaseUrl(defaultBaseUrl);
            resp.setMessage("尚未配置 IMA 凭证");
        }
        return ResponseEntity.ok(resp);
    }

    /** 保存/更新本用户的 IMA 凭证（加密存储） */
    @PutMapping
    public ResponseEntity<ImaKbConfigDto.CredentialResponse> save(
            @Valid @RequestBody ImaKbConfigDto.CredentialRequest request, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        ImaCredential c = credentialRepository.findByUserId(userId).orElse(new ImaCredential());
        c.setUserId(userId);
        // clientId/apiKey 为空则保留原有值（允许只更新 baseUrl）
        if (request.getClientId() != null && !request.getClientId().isBlank()) {
            c.setClientIdEncrypted(encryptionService.encrypt(request.getClientId()));
        }
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            c.setApiKeyEncrypted(encryptionService.encrypt(request.getApiKey()));
        }
        String baseUrl = request.getBaseUrl();
        c.setBaseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : null);

        c = credentialRepository.save(c);

        ImaKbConfigDto.CredentialResponse resp = new ImaKbConfigDto.CredentialResponse();
        resp.setConfigured(true);
        resp.setClientIdMasked(mask(c.getClientIdEncrypted() != null ? encryptionService.decrypt(c.getClientIdEncrypted()) : null));
        resp.setBaseUrl(c.getBaseUrl() != null && !c.getBaseUrl().isBlank() ? c.getBaseUrl() : defaultBaseUrl);
        resp.setMessage("保存成功");
        return ResponseEntity.ok(resp);
    }

    @DeleteMapping
    public ResponseEntity<?> delete(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        credentialRepository.findByUserId(userId).ifPresent(credentialRepository::delete);
        return ResponseEntity.ok(java.util.Map.of("message", "已清除本用户的 IMA 凭证"));
    }

    /** 用本用户的 IMA 凭证测试真实连通性 */
    @PostMapping("/test")
    public ResponseEntity<ImaKbConfigDto.TestResponse> test(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        boolean ok = imaSearchService.testConnection(userId, "kb-default");
        ImaKbConfigDto.TestResponse resp = new ImaKbConfigDto.TestResponse();
        resp.setSuccess(ok);
        resp.setMessage(ok ? "连接成功" : "连接失败：请检查本用户的 Client ID / API Key");
        return ResponseEntity.ok(resp);
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.length() <= 6) return "***";
        return s.substring(0, 3) + "***" + s.substring(s.length() - 3);
    }
}
