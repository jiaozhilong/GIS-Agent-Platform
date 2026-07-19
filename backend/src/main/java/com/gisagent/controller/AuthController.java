package com.gisagent.controller;

import com.gisagent.dto.AuthDto;
import com.gisagent.service.AuthService;
import com.gisagent.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthDto.RegisterRequest request, HttpServletRequest req) {
        try {
            AuthDto.AuthResponse response = authService.register(request);
            auditService.log(response.getUserId(), request.getUsername(), "REGISTER", "USER",
                    response.getUserId(), null, req.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request, HttpServletRequest req) {
        try {
            AuthDto.AuthResponse response = authService.login(request);
            auditService.log(response.getUserId(), request.getUsername(), "LOGIN", "USER",
                    response.getUserId(), null, req.getRemoteAddr());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
