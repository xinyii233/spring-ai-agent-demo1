package com.example.agent.infrastructure.adapter.repository.mock;

import com.example.agent.domain.activity.adapter.repository.IActivityRepository;
import com.example.agent.domain.activity.model.ActivityInfo;
import com.example.agent.domain.activity.model.ActivitySales;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 学习用模拟仓储：固定数据集中在这里，不承担正式数据库查询。
 * 不使用 @Repository 自动注册，由 app 的学习配置显式装配。
 */
public final class MockActivityRepository implements IActivityRepository {

    // 固定的历史活动，避免依赖程序运行当天的日期。
    private final Map<String, ActivityInfo> activityInfo = Map.of(
            "A001", new ActivityInfo(
                    "A001", "周末拼团", "已结束",
                    LocalDateTime.of(2026, 10, 1, 0, 0),
                    LocalDateTime.of(2026, 10, 21, 23, 59, 59)));

    private final Map<String, ActivitySales> activitySales = Map.of(
            "A001", new ActivitySales(
                    "A001", "周末拼团", 120L, new BigDecimal("9600")));

    @Override
    public Optional<ActivityInfo> findInfoById(String activityId) {
        return Optional.ofNullable(activityInfo.get(activityId));
    }

    @Override
    public Optional<ActivitySales> findSalesById(String activityId) {
        return Optional.ofNullable(activitySales.get(activityId));
    }
}
