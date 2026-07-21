package com.gisagent.controller;

import com.gisagent.entity.Organization;
import com.gisagent.entity.User;
import com.gisagent.repository.OrganizationRepository;
import com.gisagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 平台用户管理（P6-2，仅 SUPER_ADMIN 可访问）。
 * GET  /api/admin/users            用户列表（含角色/启用状态）
 * POST /api/admin/users/{id}/role  修改全局角色
 * POST /api/admin/users/{id}/toggle-enabled 启用/禁用账号
 * GET  /api/admin/organizations    组织列表（P7-1 多租户）
 * POST /api/admin/organizations    创建组织
 * POST /api/admin/users/{id}/organization 设置用户所属组织
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;

    private Long requireSuperAdmin(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        Long uid = (Long) auth.getPrincipal();
        User me = userRepository.findById(uid).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!"SUPER_ADMIN".equals(me.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要超级管理员权限");
        }
        return uid;
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers(Authentication auth) {
        requireSuperAdmin(auth);
        List<Map<String, Object>> users = userRepository.findAll().stream().map(u -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("displayName", u.getDisplayName());
            m.put("email", u.getEmail());
            m.put("role", u.getRole());
            m.put("enabled", u.getEnabled());
            m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("users", users, "total", users.size()));
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<?> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body, Authentication auth) {
        requireSuperAdmin(auth);
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        String newRole = body.get("role");
        if (!List.of("SUPER_ADMIN", "ADMIN", "USER").contains(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法角色"));
        }
        Long me = (Long) auth.getPrincipal();
        if (me.equals(id) && !"SUPER_ADMIN".equals(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能取消自己的超级管理员角色"));
        }
        target.setRole(newRole);
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("ok", true, "role", newRole));
    }

    @PostMapping("/users/{id}/toggle-enabled")
    public ResponseEntity<?> toggleEnabled(@PathVariable Long id, Authentication auth) {
        requireSuperAdmin(auth);
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        Long me = (Long) auth.getPrincipal();
        if (me.equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能禁用自己的账号"));
        }
        target.setEnabled(!Boolean.TRUE.equals(target.getEnabled()));
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("ok", true, "enabled", target.getEnabled()));
    }

    /** 管理员创建/邀请新用户（指定用户名、初始密码、角色），注册后用户可自行修改密码 */
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body, Authentication auth) {
        requireSuperAdmin(auth);
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        String displayName = (String) body.getOrDefault("displayName", null);
        String email = (String) body.getOrDefault("email", null);
        String role = (String) body.getOrDefault("role", "USER");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }
        if (!List.of("SUPER_ADMIN", "ADMIN", "USER").contains(role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法角色"));
        }
        if (userRepository.existsByUsername(username.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        Long orgId = organizationRepository.findByName("默认组织")
                .map(Organization::getId)
                .orElseGet(() -> organizationRepository.save(
                        Organization.builder().name("默认组织").slug("default").build()).getId());
        User user = User.builder()
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .role(role)
                .enabled(true)
                .displayName(displayName != null ? displayName : null)
                .email(email != null ? email : null)
                .organizationId(orgId)
                .build();
        user = userRepository.save(user);
        return ResponseEntity.ok(Map.of(
                "id", user.getId(), "username", user.getUsername(),
                "displayName", user.getDisplayName(), "role", user.getRole()));
    }

    // ===== 组织（租户）管理（P7-1，仅 SUPER_ADMIN）=====

    @GetMapping("/organizations")
    public ResponseEntity<?> listOrganizations(Authentication auth) {
        requireSuperAdmin(auth);
        List<Map<String, Object>> orgs = organizationRepository.findAll().stream().map(o -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("name", o.getName());
            m.put("slug", o.getSlug());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("organizations", orgs, "total", orgs.size()));
    }

    @PostMapping("/organizations")
    public ResponseEntity<?> createOrganization(@RequestBody Map<String, String> body, Authentication auth) {
        requireSuperAdmin(auth);
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "组织名称不能为空"));
        }
        if (organizationRepository.existsByName(name.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "组织已存在"));
        }
        Organization org = organizationRepository.save(Organization.builder()
                .name(name.trim())
                .slug("org-" + System.nanoTime())
                .build());
        return ResponseEntity.ok(Map.of("id", org.getId(), "name", org.getName(), "slug", org.getSlug()));
    }

    @PostMapping("/users/{id}/organization")
    public ResponseEntity<?> setUserOrganization(@PathVariable Long id,
                                                 @RequestBody Map<String, Long> body,
                                                 Authentication auth) {
        requireSuperAdmin(auth);
        User target = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        Long orgId = body.get("organizationId");
        if (orgId == null || !organizationRepository.existsById(orgId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "组织不存在"));
        }
        target.setOrganizationId(orgId);
        userRepository.save(target);
        return ResponseEntity.ok(Map.of("ok", true, "userId", id, "organizationId", orgId));
    }
}
