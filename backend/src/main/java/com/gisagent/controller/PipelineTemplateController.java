package com.gisagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gisagent.dto.TemplateSaveRequest;
import com.gisagent.entity.PipelineTemplate;
import com.gisagent.entity.TemplateFavorite;
import com.gisagent.entity.TemplateLike;
import com.gisagent.entity.User;
import com.gisagent.repository.PipelineTemplateRepository;
import com.gisagent.repository.TemplateFavoriteRepository;
import com.gisagent.repository.TemplateLikeRepository;
import com.gisagent.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/templates")
@Slf4j
public class PipelineTemplateController {

    /** 引擎已注册的工具类型（与 PipelineEngine.TOOL_BY_TYPE / 前端 TOOL_META 对齐） */
    private static final Set<String> ALLOWED_TOOL_TYPES = Set.of(
            "REQUIREMENT_ANALYSIS",
            "PRODUCT_MATCHING",
            "CASE_RECOMMEND",
            "COMPETITOR_ANALYSIS",
            "ARCHITECTURE_DIAGRAM",
            "SOLUTION_OUTLINE",
            "SOLUTION_QC",
            "SOLUTION_OUTPUT");

    private final PipelineTemplateRepository templateRepository;
    private final TemplateLikeRepository likeRepository;
    private final TemplateFavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PipelineTemplateController(PipelineTemplateRepository templateRepository,
                                      TemplateLikeRepository likeRepository,
                                      TemplateFavoriteRepository favoriteRepository,
                                      UserRepository userRepository,
                                      ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) return null;
        return (Long) auth.getPrincipal();
    }

    /** 简单列表（保持原有行为，按 category 过滤） */
    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String category) {
        List<PipelineTemplate> list;
        if (category != null && !category.isBlank()) {
            list = templateRepository.findByCategory(category);
        } else {
            list = templateRepository.findAllByOrderByBuiltinDescIdAsc();
        }
        return ResponseEntity.ok(list);
    }

    /** 模板详情（按 templateKey） */
    @GetMapping("/{key}")
    public ResponseEntity<?> getByKey(@PathVariable String key) {
        return templateRepository.findByTemplateKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 模板市场：官方 + 社区 + 我的，带点赞/收藏计数与当前用户态。
     * scope: all(官方+已上架社区) | official | community | mine(当前用户)
     */
    @GetMapping("/market")
    public ResponseEntity<?> market(@RequestParam(defaultValue = "all") String scope,
                                    @RequestParam(required = false) String keyword,
                                    Authentication auth) {
        Long uid = uid(auth);
        List<PipelineTemplate> base;
        if ("official".equals(scope)) {
            base = templateRepository.findByBuiltinTrue();
        } else if ("community".equals(scope)) {
            base = templateRepository.findByCategoryAndPublishedTrue("community");
        } else if ("mine".equals(scope)) {
            base = uid == null ? List.of() : templateRepository.findByOwnerId(uid);
        } else {
            List<PipelineTemplate> official = templateRepository.findByBuiltinTrue();
            List<PipelineTemplate> community = templateRepository.findByCategoryAndPublishedTrue("community");
            base = new ArrayList<>();
            base.addAll(official);
            base.addAll(community);
        }
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            base = base.stream()
                    .filter(t -> (t.getName() != null && t.getName().toLowerCase().contains(k))
                            || (t.getDescription() != null && t.getDescription().toLowerCase().contains(k)))
                    .collect(Collectors.toList());
        }
        // 预取作者名
        Set<Long> ownerIds = base.stream().map(PipelineTemplate::getOwnerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> ownerNames = ownerIds.isEmpty() ? Map.of()
                : userRepository.findAllById(ownerIds).stream().collect(Collectors.toMap(User::getId, User::getUsername));
        List<Map<String, Object>> result = base.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("templateKey", t.getTemplateKey());
            m.put("name", t.getName());
            m.put("category", t.getCategory());
            m.put("description", t.getDescription());
            m.put("toolChain", parseChain(t.getToolChainJson()));
            m.put("estimatedTime", t.getEstimatedTime());
            m.put("usageCount", t.getUsageCount() == null ? 0 : t.getUsageCount());
            m.put("likeCount", t.getLikeCount() == null ? 0 : t.getLikeCount());
            m.put("favoriteCount", t.getFavoriteCount() == null ? 0 : t.getFavoriteCount());
            m.put("ownerId", t.getOwnerId());
            m.put("ownerName", t.getOwnerId() == null ? "官方" : ownerNames.getOrDefault(t.getOwnerId(), "用户" + t.getOwnerId()));
            m.put("isLiked", uid != null && likeRepository.existsByTemplateIdAndUserId(t.getId(), uid));
            m.put("isFavorited", uid != null && favoriteRepository.existsByTemplateIdAndUserId(t.getId(), uid));
            m.put("canEdit", uid != null && Objects.equals(t.getOwnerId(), uid));
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** 点赞 / 取消点赞（toggle） */
    @PostMapping("/{id}/like")
    @Transactional
    public ResponseEntity<?> toggleLike(@PathVariable Long id, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        PipelineTemplate tpl = templateRepository.findById(id).orElse(null);
        if (tpl == null) return ResponseEntity.notFound().build();
        boolean nowLiked;
        if (likeRepository.existsByTemplateIdAndUserId(id, uid)) {
            likeRepository.deleteByTemplateIdAndUserId(id, uid);
            nowLiked = false;
        } else {
            likeRepository.save(TemplateLike.builder().templateId(id).userId(uid).createdAt(Instant.now()).build());
            nowLiked = true;
        }
        long cnt = likeRepository.countByTemplateId(id);
        tpl.setLikeCount(cnt);
        templateRepository.save(tpl);
        return ResponseEntity.ok(Map.of("liked", nowLiked, "likeCount", cnt));
    }

    /** 收藏 / 取消收藏（toggle） */
    @PostMapping("/{id}/favorite")
    @Transactional
    public ResponseEntity<?> toggleFavorite(@PathVariable Long id, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        PipelineTemplate tpl = templateRepository.findById(id).orElse(null);
        if (tpl == null) return ResponseEntity.notFound().build();
        boolean nowFav;
        if (favoriteRepository.existsByTemplateIdAndUserId(id, uid)) {
            favoriteRepository.deleteByTemplateIdAndUserId(id, uid);
            nowFav = false;
        } else {
            favoriteRepository.save(TemplateFavorite.builder().templateId(id).userId(uid).createdAt(Instant.now()).build());
            nowFav = true;
        }
        long cnt = favoriteRepository.countByTemplateId(id);
        tpl.setFavoriteCount(cnt);
        templateRepository.save(tpl);
        return ResponseEntity.ok(Map.of("favorited", nowFav, "favoriteCount", cnt));
    }

    /**
     * 保存自定义模板：category=mine(默认，仅自己) 或 community(发布到社区市场)。
     * 自动生成唯一 templateKey，校验名称与工具链合法性。
     */
    @PostMapping
    public ResponseEntity<?> save(@RequestBody TemplateSaveRequest req, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "模板名称不能为空"));
        }
        if (req.getToolChain() == null || req.getToolChain().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "工具链不能为空，请至少添加一个工具节点"));
        }
        for (String type : req.getToolChain()) {
            if (!ALLOWED_TOOL_TYPES.contains(type)) {
                return ResponseEntity.badRequest().body(Map.of("message", "包含非法工具类型: " + type));
            }
        }
        String category = "community".equalsIgnoreCase(req.getCategory()) ? "community" : "mine";
        String key = (category.equals("community") ? "comm_" : "mine_") + UUID.randomUUID().toString().replace("-", "");
        String chainJson;
        try {
            chainJson = objectMapper.writeValueAsString(req.getToolChain());
        } catch (Exception e) {
            log.warn("工具链序列化失败", e);
            return ResponseEntity.badRequest().body(Map.of("message", "工具链格式错误"));
        }
        PipelineTemplate tpl = PipelineTemplate.builder()
                .templateKey(key)
                .name(req.getName().trim())
                .category(category)
                .description(req.getDescription() == null ? "" : req.getDescription())
                .toolChainJson(chainJson)
                .estimatedTime(req.getEstimatedTime())
                .builtin(false)
                .ownerId(uid)
                .usageCount(0L)
                .likeCount(0L)
                .favoriteCount(0L)
                .published(true)
                .build();
        PipelineTemplate saved = templateRepository.save(tpl);
        log.info("保存模板成功: key={}, name={}, category={}, 工具数={}",
                key, saved.getName(), category, req.getToolChain().size());
        return ResponseEntity.status(201).body(saved);
    }

    /**
     * 发布到社区 / 撤回为私有（仅作者本人，非内置）。
     */
    @PostMapping("/{id}/publish")
    @Transactional
    public ResponseEntity<?> publish(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean community, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        PipelineTemplate tpl = templateRepository.findById(id).orElse(null);
        if (tpl == null) return ResponseEntity.notFound().build();
        if (tpl.isBuiltin()) return ResponseEntity.badRequest().body(Map.of("message", "内置模板不可发布到社区"));
        if (tpl.getOwnerId() != null && !tpl.getOwnerId().equals(uid))
            return ResponseEntity.status(403).body(Map.of("message", "仅模板作者可操作"));
        tpl.setCategory(community ? "community" : "mine");
        tpl.setPublished(true);
        templateRepository.save(tpl);
        log.info("模板发布状态变更: id={}, community={}", id, community);
        return ResponseEntity.ok(Map.of("message", community ? "已发布到社区" : "已撤回为私有", "category", tpl.getCategory()));
    }

    /**
     * 删除自定义模板（仅作者本人，且非内置）。
     */
    @DeleteMapping("/{key}")
    @Transactional
    public ResponseEntity<?> remove(@PathVariable String key, Authentication auth) {
        Long uid = uid(auth);
        return templateRepository.findByTemplateKey(key)
                .map(tpl -> {
                    if (tpl.isBuiltin()) {
                        return ResponseEntity.badRequest().body(Map.of("message", "内置模板不可删除"));
                    }
                    if (uid != null && tpl.getOwnerId() != null && !tpl.getOwnerId().equals(uid)) {
                        return ResponseEntity.status(403).body(Map.of("message", "仅模板作者可删除"));
                    }
                    likeRepository.deleteByTemplateIdAndUserId(tpl.getId(), uid == null ? -1L : uid);
                    templateRepository.delete(tpl);
                    log.info("删除自定义模板: key={}", key);
                    return ResponseEntity.ok(Map.of("message", "已删除", "key", key));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private List<String> parseChain(String raw) {
        if (raw == null) return List.of();
        try { return objectMapper.readValue(raw, List.class); }
        catch (Exception e) { return List.of(); }
    }
}
