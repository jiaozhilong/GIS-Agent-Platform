package com.gisagent.controller;

import com.gisagent.dto.LlmProviderDto;
import com.gisagent.entity.LlmProvider;
import com.gisagent.repository.LlmProviderRepository;
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

    public LlmProviderController(LlmProviderRepository providerRepository) {
        this.providerRepository = providerRepository;
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
                .apiKeyEncrypted(request.getApiKey()) // TODO: 加密存储
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
                    // TODO: 实际调用模型 API 测试连通性
                    LlmProviderDto.TestResponse response = new LlmProviderDto.TestResponse();
                    response.setSuccess(true);
                    response.setResponseTimeMs(150L);
                    response.setMessage("连接成功");
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
        r.setIsDefault(p.getIsDefault());
        r.setHasApiKey(p.getApiKeyEncrypted() != null && !p.getApiKeyEncrypted().isEmpty());
        return r;
    }
}
