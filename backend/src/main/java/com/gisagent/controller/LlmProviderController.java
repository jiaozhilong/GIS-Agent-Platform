package com.gisagent.controller;

import com.gisagent.dto.LlmProviderDto;
import com.gisagent.entity.LlmProvider;
import com.gisagent.repository.LlmProviderRepository;
import com.gisagent.service.LlmService;
import com.gisagent.util.EncryptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/providers")
public class LlmProviderController {

    private final LlmProviderRepository providerRepository;
    private final EncryptionService encryptionService;
    private final LlmService llmService;

    public LlmProviderController(LlmProviderRepository providerRepository,
                                 EncryptionService encryptionService,
                                 LlmService llmService) {
        this.providerRepository = providerRepository;
        this.encryptionService = encryptionService;
        this.llmService = llmService;
    }

    @GetMapping
    public ResponseEntity<List<LlmProviderDto.ProviderResponse>> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<LlmProviderDto.ProviderResponse> providers = providerRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(providers);
    }

    @PostMapping
    public ResponseEntity<LlmProviderDto.ProviderResponse> create(
            @Valid @RequestBody LlmProviderDto.CreateRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        // 如果设为默认，先取消其他默认
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            providerRepository.findByUserIdAndIsDefaultTrue(userId)
                    .forEach(p -> {
                        p.setIsDefault(false);
                        providerRepository.save(p);
                    });
        }

        LlmProvider provider = LlmProvider.builder()
                .userId(userId)
                .name(request.getName())
                .providerType(request.getProviderType())
                .endpoint(request.getEndpoint())
                .apiKeyEncrypted(encryptionService.encrypt(request.getApiKey())) // F-A: 加密存储
                .model(request.getModel())
                .isDefault(request.getIsDefault() != null && request.getIsDefault())
                .build();

        provider = providerRepository.save(provider);
        return ResponseEntity.ok(toResponse(provider));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return providerRepository.findByIdAndUserId(id, userId)
                .map(p -> {
                    providerRepository.delete(p);
                    return ResponseEntity.ok(Map.of("message", "删除成功"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<LlmProviderDto.TestResponse> test(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return providerRepository.findByIdAndUserId(id, userId)
                .map(p -> {
                    // F-B: 解密后真实调用模型 API 验证连通性（非恒真占位）
                    String key = encryptionService.decrypt(p.getApiKeyEncrypted());
                    long start = System.currentTimeMillis();
                    boolean ok = llmService.testConnect(p.getEndpoint(), key, p.getModel());
                    long cost = System.currentTimeMillis() - start;
                    LlmProviderDto.TestResponse response = new LlmProviderDto.TestResponse();
                    response.setSuccess(ok);
                    response.setResponseTimeMs(cost);
                    response.setMessage(ok ? "连接成功" : "连接失败：无法调用模型 API（请检查 endpoint / apiKey / model 或网络）");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private LlmProviderDto.ProviderResponse toResponse(LlmProvider p) {
        LlmProviderDto.ProviderResponse r = new LlmProviderDto.ProviderResponse();
        r.setId(p.getId());
        r.setName(p.getName());
        r.setProviderType(p.getProviderType());
        r.setEndpoint(p.getEndpoint());
        r.setModel(p.getModel());
        r.setIsDefault(p.getIsDefault());
        r.setHasApiKey(p.getApiKeyEncrypted() != null && !p.getApiKeyEncrypted().isEmpty());
        return r;
    }
}
