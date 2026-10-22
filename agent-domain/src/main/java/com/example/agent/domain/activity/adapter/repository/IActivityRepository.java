package com.example.agent.domain.activity.adapter.repository;

import com.example.agent.domain.activity.model.ActivityInfo;
import com.example.agent.domain.activity.model.ActivitySales;

import java.util.Optional;

/**
 * 活动查询契约：调用方不需要知道数据来自模拟实现、数据库还是业务接口。
 * Optional.empty() 表示没有找到活动，不表示金额为零。
 */
public interface IActivityRepository {

    Optional<ActivityInfo> findInfoById(String activityId);

    Optional<ActivitySales> findSalesById(String activityId);
}
