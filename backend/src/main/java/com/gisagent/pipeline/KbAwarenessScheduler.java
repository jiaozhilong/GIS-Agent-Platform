package com.gisagent.pipeline;

import com.gisagent.service.KbAwarenessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库自动感知调度器（P3-1）。
 * 周期性调用 KbAwarenessService.syncAll() 拉取 IMA 知识库更新事件，
 * 发现变更即把相应用户的项目标记为"知识库有更新"。
 */
@Component
@Slf4j
public class KbAwarenessScheduler {

    private final KbAwarenessService kbAwarenessService;

    public KbAwarenessScheduler(KbAwarenessService kbAwarenessService) {
        this.kbAwarenessService = kbAwarenessService;
    }

    /** 每 60 秒同步一次（dev/联调节奏；生产可调大） */
    @Scheduled(fixedDelay = 60_000)
    public void scheduledSync() {
        try {
            var r = kbAwarenessService.syncAll();
            if (r.eventCount() > 0 || r.dirtyProjectCount() > 0) {
                log.info("知识库感知同步：用户 {} 个，事件 {} 条，标记待重生成项目 {} 个",
                        r.userCount(), r.eventCount(), r.dirtyProjectCount());
            }
        } catch (Exception e) {
            log.warn("知识库感知同步异常：{}", e.getMessage());
        }
    }
}
