package com.gisagent.controller;

import com.gisagent.entity.User;
import com.gisagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 平台用户管理（P6-2，仅 SUPER_ADMIN 可访问）。
 * GET  /api/admin/users            用户列表（含角色/启用状态）
 * POST /api/admin/users/{id}/role  修改全局角色
 * POST /api/admin/users/{id}/toggle-enabled 启用/禁用账号
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;

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
}
