package com.example.agent.domain.activity.model;

import java.math.BigDecimal;

/** 活动累计支付数据：订单数包含后来退款的订单，销售额为退款前金额。 */
public record ActivitySales(
        String activityId,
        String activityName,
        long paidOrderCount,
        BigDecimal salesAmount) {
}
