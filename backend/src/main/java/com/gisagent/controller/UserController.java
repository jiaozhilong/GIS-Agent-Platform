package com.gisagent.controller;

import com.gisagent.entity.User;
import com.gisagent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 当前用户资料（P6-3）。
 * GET  /api/users/me          当前用户资料
 * POST /api/users/me          更新 displayName / email
 * POST /api/users/me/password 修改密码（校验旧密码）
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        }
        return (Long) auth.getPrincipal();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        Long id = uid(auth);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "用户不存在"));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("displayName", u.getDisplayName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole());
        m.put("enabled", u.getEnabled());
        return ResponseEntity.ok(m);
    }

    @PostMapping("/me")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> body, Authentication auth) {
        Long id = uid(auth);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "用户不存在"));
        if (body.containsKey("displayName")) u.setDisplayName(body.get("displayName"));
        if (body.containsKey("email")) u.setEmail(body.get("email"));
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/me/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, Authentication auth) {
        Long id = uid(auth);
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "用户不存在"));
        String oldP = body.get("oldPassword");
        String newP = body.get("newPassword");
        if (oldP == null || newP == null || newP.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "新密码至少 6 位"));
        }
        if (!passwordEncoder.matches(oldP, u.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "原密码错误"));
        }
        u.setPassword(passwordEncoder.encode(newP));
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
