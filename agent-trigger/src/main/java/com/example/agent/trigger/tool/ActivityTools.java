package com.example.agent.trigger.tool;

import com.example.agent.domain.activity.adapter.repository.IActivityRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class ActivityTools {

    private final IActivityRepository activityRepository;

    // 依赖查询接口，不知道底层使用模拟数据还是数据库。
    public ActivityTools(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Tool(description = "根据活动编号查询活动销售数据，返回活动名称、订单数和销售额")
    public String getActivitySales(
            @ToolParam(description = "活动编号，例如 A001") String activityId) {

        System.out.println("[工具执行] getActivitySales，参数：" + activityId);

        // 工具只负责调用查询接口并组织返回内容，不保存固定业务数据。
        return activityRepository.findSalesById(activityId)
                .map(sales -> """
                        活动编号：%s
                        活动名称：%s
                        统计范围：本次活动累计数据
                        支付成功订单数：%d单，包含后来成功退款的订单
                        销售额：%s元，为扣除退款前的支付金额
                        """.formatted(sales.activityId(), sales.activityName(),
                        sales.paidOrderCount(), sales.salesAmount().toPlainString()))
                .orElse("未找到活动：" + activityId);
    }
}
