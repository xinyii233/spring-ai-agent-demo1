package com.example.agent.trigger.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class ActivityTools {

    @Tool(description = "根据活动编号查询活动销售数据，返回活动名称、订单数和销售额")
    public String getActivitySales(
            @ToolParam(description = "活动编号，例如 A001") String activityId) {

        System.out.println("[工具执行] getActivitySales，参数：" + activityId);

        if ("A001".equals(activityId)) {
            return "活动编号：A001，活动名称：周末拼团，订单数：120，销售额：9600元";
        }

        return "未找到活动：" + activityId;
    }

    public static void main(String[] args) {
        ActivityTools tools = new ActivityTools();
        System.out.println(tools.getActivitySales("A001"));
        System.out.println(tools.getActivitySales("A999"));
    }
}