package com.gisagent.controller;

import com.gisagent.dto.SkillDto;
import com.gisagent.entity.Skill;
import com.gisagent.repository.SkillRepository;
import com.gisagent.service.SkillService;
import com.gisagent.util.EncryptionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 可编排 Skill（外部能力）管理接口。
 * 每个 Skill 绑定一个流水线工具节点（toolType），运行时由引擎按 toolType 查找已启用
 * API_ENDPOINT 类型 Skill 替代内置逻辑。按 ownerId（当前登录用户）隔离。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillsController {

    private final SkillRepository skillRepository;
    private final SkillService skillService;
    private final EncryptionService encryptionService;

    public SkillsController(SkillRepository skillRepository, SkillService skillService, EncryptionService encryptionService) {
        this.skillRepository = skillRepository;
        this.skillService = skillService;
        this.encryptionService = encryptionService;
    }

    @GetMapping
    public ResponseEntity<List<SkillDto.Response>> list(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<SkillDto.Response> list = skillRepository.findByOwnerId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SkillDto.Response> get(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return skillRepository.findById(id)
                .filter(s -> s.getOwnerId().equals(userId))
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SkillDto.Response> create(@Valid @RequestBody SkillDto.CreateRequest req, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Skill skill = Skill.builder()
                .ownerId(userId)
                .name(req.getName())
                .description(req.getDescription())
                .type(req.getType() != null ? req.getType() : "API_ENDPOINT")
                .toolType(req.getToolType())
                .endpointUrl(req.getEndpointUrl())
                .requestTemplate(req.getRequestTemplate())
                .gitRepoUrl(req.getGitRepoUrl())
                .gitRef(req.getGitRef())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .build();
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            skill.setApiKeyEncrypted(encryptionService.encrypt(req.getApiKey()));
        }
        skill = skillRepository.save(skill);
        return ResponseEntity.ok(toResponse(skill));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SkillDto.Response> update(@PathVariable Long id,
                                                    @Valid @RequestBody SkillDto.UpdateRequest req,
                                                    Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return skillRepository.findById(id)
                .filter(s -> s.getOwnerId().equals(userId))
                .map(s -> {
                    if (req.getName() != null) s.setName(req.getName());
                    if (req.getDescription() != null) s.setDescription(req.getDescription());
                    if (req.getType() != null) s.setType(req.getType());
                    if (req.getToolType() != null) s.setToolType(req.getToolType());
                    if (req.getEndpointUrl() != null) s.setEndpointUrl(req.getEndpointUrl());
                    if (req.getRequestTemplate() != null) s.setRequestTemplate(req.getRequestTemplate());
                    if (req.getGitRepoUrl() != null) s.setGitRepoUrl(req.getGitRepoUrl());
                    if (req.getGitRef() != null) s.setGitRef(req.getGitRef());
                    if (req.getEnabled() != null) s.setEnabled(req.getEnabled());
                    if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
                        s.setApiKeyEncrypted(encryptionService.encrypt(req.getApiKey()));
                    }
                    return ResponseEntity.ok(toResponse(skillRepository.save(s)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return skillRepository.findById(id)
                .filter(s -> s.getOwnerId().equals(userId))
                .map(s -> {
                    skillRepository.delete(s);
                    return ResponseEntity.ok(Map.of("message", "删除成功"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<?> test(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return skillRepository.findById(id)
                .filter(s -> s.getOwnerId().equals(userId))
                .map(s -> {
                    boolean ok = skillService.testSkill(s);
                    return ResponseEntity.ok(Map.of("success", ok,
                            "message", ok ? "连接成功" : "连接失败"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private SkillDto.Response toResponse(Skill s) {
        SkillDto.Response r = new SkillDto.Response();
        r.setId(s.getId());
        r.setName(s.getName());
        r.setDescription(s.getDescription());
        r.setType(s.getType());
        r.setToolType(s.getToolType());
        r.setEndpointUrl(s.getEndpointUrl());
        r.setRequestTemplate(s.getRequestTemplate());
        r.setGitRepoUrl(s.getGitRepoUrl());
        r.setGitRef(s.getGitRef());
        r.setEnabled(s.getEnabled());
        r.setHasApiKey(s.getApiKeyEncrypted() != null && !s.getApiKeyEncrypted().isBlank());
        return r;
    }
}
