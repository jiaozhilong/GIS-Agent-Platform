package com.gisagent.service;

import com.gisagent.entity.Notification;
import com.gisagent.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 站内通知服务（P5-5）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void send(Long userId, String type, String title, String body, String link) {
        Notification n = Notification.builder()
                .userId(userId).type(type).title(title).body(body).link(link)
                .isRead(false).build();
        notificationRepository.save(n);
    }

    public List<Notification> listForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Map<String, Object> summary(Long userId) {
        long unread = notificationRepository.countByUserIdAndIsReadFalse(userId);
        List<Notification> recent = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().limit(20).toList();
        return Map.of("unread", unread, "items", (Object) recent);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
