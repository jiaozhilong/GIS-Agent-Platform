package com.gisagent.service;

import com.gisagent.entity.AuditLog;
import com.gisagent.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 审计日志服务（P5-4）。
 * 记录关键操作：登录/项目CRUD/流水线运行/导出/权限变更。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(Long userId, String username, String action, String targetType, Long targetId, String detail, String ip) {
        try {
            AuditLog entry = AuditLog.builder()
                    .userId(userId).username(username)
                    .action(action).targetType(targetType).targetId(targetId)
                    .detail(detail).ipAddress(ip)
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("审计日志写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    public List<AuditLog> recent(int limit) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    public List<AuditLog> byUser(Long userId, int limit) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit));
    }
}
