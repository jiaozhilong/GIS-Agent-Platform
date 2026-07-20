package com.gisagent.controller;

import com.gisagent.dto.BillingDto;
import com.gisagent.entity.User;
import com.gisagent.entity.UsageQuota;
import com.gisagent.repository.UserRepository;
import com.gisagent.service.BillingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 计费纵深接口（P8-1）。
 *  GET  /api/billing/quota              查看配额（超管可指定 orgId；普通用户看本组织）
 *  PUT  /api/billing/quota              设置配额（仅 SUPER_ADMIN）
 *  GET  /api/billing/invoices           查看账单（超管可指定 orgId 或看全部）
 *  POST /api/billing/invoices/generate  按月生成账单（仅 SUPER_ADMIN）
 */
@RestController
@RequestMapping("/api/billing")
@Slf4j
public class BillingController {

    private final BillingService billingService;
    private final UserRepository userRepository;

    public BillingController(BillingService billingService, UserRepository userRepository) {
        this.billingService = billingService;
        this.userRepository = userRepository;
    }

    private Long uid(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long)) return null;
        return (Long) auth.getPrincipal();
    }

    private User me(Long uid) {
        return userRepository.findById(uid).orElseThrow(
                () -> new ResponseStatusException(UNAUTHORIZED, "用户不存在"));
    }

    private Long resolveOrgId(User u, Long requestedOrgId) {
        boolean superAdmin = "SUPER_ADMIN".equals(u.getRole());
        if (superAdmin && requestedOrgId != null) return requestedOrgId; // 超管可下钻任意组织
        if (u.getOrganizationId() != null) return u.getOrganizationId();
        if (superAdmin) return null; // 超管未指定且无本组织时，按全部处理（由 service 决定）
        throw new ResponseStatusException(FORBIDDEN, "当前账号未分配组织");
    }

    @GetMapping("/quota")
    public ResponseEntity<?> getQuota(@RequestParam(value = "orgId", required = false) Long orgId,
                                      Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            User u = me(uid);
            Long target = resolveOrgId(u, orgId);
            if (target == null) return ResponseEntity.ok(Map.of("scope", "all", "quotas", billingService.allQuotas()));
            Optional<UsageQuota> q = billingService.getQuota(target);
            return ResponseEntity.ok(q.map(quota -> (Object) quota).orElse(null));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", msgOf(e)));
        }
    }

    /** 超管手动巡检某组织配额（触发超限告警，等价于运行结束时的自动检查） */
    @PostMapping("/quota/check")
    public ResponseEntity<?> checkQuota(@RequestParam(value = "orgId", required = false) Long orgId,
                                        Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            User u = me(uid);
            Long target = resolveOrgId(u, orgId);
            if (target == null) throw new ResponseStatusException(FORBIDDEN, "当前账号未分配组织");
            billingService.evaluateOrg(target, uid);
            return ResponseEntity.ok(Map.of("checked", true, "organizationId", target));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", msgOf(e)));
        }
    }

    @PutMapping("/quota")
    public ResponseEntity<?> setQuota(@RequestBody BillingDto.QuotaSetRequest req, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            User u = me(uid);
            if (!"SUPER_ADMIN".equals(u.getRole())) throw new ResponseStatusException(FORBIDDEN, "仅超管可设置配额");
            if (req.getOrganizationId() == null) throw new ResponseStatusException(UNAUTHORIZED, "organizationId 必填");
            UsageQuota q = billingService.setQuota(req.getOrganizationId(), req.getTokenLimit(),
                    req.getWarnThreshold(), uid, u.getUsername());
            return ResponseEntity.ok(q);
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", msgOf(e)));
        }
    }

    @GetMapping("/invoices")
    public ResponseEntity<?> getInvoices(@RequestParam(value = "orgId", required = false) Long orgId,
                                         Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            User u = me(uid);
            boolean superAdmin = "SUPER_ADMIN".equals(u.getRole());
            Long target = superAdmin ? orgId : u.getOrganizationId();
            if (!superAdmin && target == null) throw new ResponseStatusException(FORBIDDEN, "当前账号未分配组织");
            return ResponseEntity.ok(billingService.getInvoices(target));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", msgOf(e)));
        }
    }

    @PostMapping("/invoices/generate")
    public ResponseEntity<?> generateInvoices(@RequestParam(value = "month", required = false) String month,
                                              Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return ResponseEntity.status(401).body(Map.of("message", "请先登录"));
        try {
            User u = me(uid);
            if (!"SUPER_ADMIN".equals(u.getRole())) throw new ResponseStatusException(FORBIDDEN, "仅超管可生成账单");
            return ResponseEntity.ok(billingService.generateInvoices(month, uid, u.getUsername()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(statusOf(e)).body(Map.of("message", msgOf(e)));
        }
    }

    private int statusOf(RuntimeException e) {
        if (e instanceof org.springframework.web.server.ResponseStatusException rse) return rse.getStatusCode().value();
        return 400;
    }

    private String msgOf(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getName();
    }
}
