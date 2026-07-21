package com.gisagent.controller;

import com.gisagent.dto.ImaKbConfigDto;
import com.gisagent.connector.IMAKnowledgeBaseConnector.KBInfo;
import com.gisagent.entity.ImaKbConfig;
import com.gisagent.repository.ImaKbConfigRepository;
import com.gisagent.service.ImaSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ima")
public class ImaKbConfigController {

    private final ImaKbConfigRepository configRepository;
    private final ImaSearchService imaSearchService;

    public ImaKbConfigController(ImaKbConfigRepository configRepository,
                                  ImaSearchService imaSearchService) {
        this.configRepository = configRepository;
        this.imaSearchService = imaSearchService;
    }

    @GetMapping("/configs")
    public ResponseEntity<List<ImaKbConfigDto.ConfigResponse>> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<ImaKbConfigDto.ConfigResponse> configs = configRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(configs);
    }

    /**
     * 从 IMA 远端拉取该用户可访问的知识库列表（订阅 + 自建）。
     * 返回 KBInfo，前端据此展示并允许用户勾选启用/停用。
     */
    @GetMapping("/kb-list")
    public ResponseEntity<?> kbList(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<KBInfoView> list = imaSearchService.listKnowledgeBases(userId)
                .stream()
                .map(k -> new KBInfoView(k.kbId(), k.kbName(), k.kbType(), k.docCount(),
                        configRepository.findByUserIdAndKbId(userId, k.kbId()).map(ImaKbConfig::getEnabled).orElse(false)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("list", list));
    }

    /** kb-list 响应视图：包含该库是否已在本地配置并启用 */
    public record KBInfoView(String kbId, String kbName, String kbType, long docCount, boolean configured) {}

    @PostMapping("/configs")
    public ResponseEntity<ImaKbConfigDto.ConfigResponse> create(
            @Valid @RequestBody ImaKbConfigDto.CreateRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        // 检查是否已存在
        if (configRepository.findByUserIdAndKbId(userId, request.getKbId()).isPresent()) {
            return ResponseEntity.badRequest().build();
        }

        ImaKbConfig config = ImaKbConfig.builder()
                .userId(userId)
                .kbId(request.getKbId())
                .kbName(request.getKbName())
                .kbType(request.getKbType())
                .purpose(request.getPurpose())
                .searchWeight(request.getSearchWeight())
                .enabled(true)
                .build();

        config = configRepository.save(config);
        return ResponseEntity.ok(toResponse(config));
    }

    @PutMapping("/configs/{id}")
    public ResponseEntity<ImaKbConfigDto.ConfigResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ImaKbConfigDto.UpdateRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return configRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .map(c -> {
                    if (request.getKbName() != null) c.setKbName(request.getKbName());
                    if (request.getPurpose() != null) c.setPurpose(request.getPurpose());
                    if (request.getSearchWeight() != null) c.setSearchWeight(request.getSearchWeight());
                    if (request.getEnabled() != null) c.setEnabled(request.getEnabled());
                    return ResponseEntity.ok(toResponse(configRepository.save(c)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/configs/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return configRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .map(c -> {
                    configRepository.delete(c);
                    return ResponseEntity.ok(Map.of("message", "删除成功"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/configs/{id}/test")
    public ResponseEntity<ImaKbConfigDto.TestResponse> test(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return configRepository.findById(id)
                .filter(c -> c.getUserId().equals(userId))
                .map(c -> {
                    boolean connected = imaSearchService.testConnection(userId, c.getKbId());
                    ImaKbConfigDto.TestResponse response = new ImaKbConfigDto.TestResponse();
                    response.setSuccess(connected);
                    response.setMessage(connected ? "连接成功" : (c.getKbId() != null ? "连接失败：请检查本用户的 IMA 凭证" : "连接失败"));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private ImaKbConfigDto.ConfigResponse toResponse(ImaKbConfig c) {
        ImaKbConfigDto.ConfigResponse r = new ImaKbConfigDto.ConfigResponse();
        r.setId(c.getId());
        r.setKbId(c.getKbId());
        r.setKbName(c.getKbName());
        r.setKbType(c.getKbType());
        r.setPurpose(c.getPurpose());
        r.setSearchWeight(c.getSearchWeight());
        r.setEnabled(c.getEnabled());
        return r;
    }
}
