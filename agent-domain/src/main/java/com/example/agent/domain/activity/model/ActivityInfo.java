package com.example.agent.domain.activity.model;

import java.time.LocalDateTime;

/** 活动基本信息。这里定义数据含义，不保存任何模拟数据。 */
public record ActivityInfo(
        String activityId,
        String activityName,
        String status,
        LocalDateTime startTime,
        LocalDateTime endTime) {
}
