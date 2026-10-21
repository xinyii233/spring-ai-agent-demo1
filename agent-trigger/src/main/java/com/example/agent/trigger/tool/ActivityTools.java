package com.example.agent.trigger.tool;

public class ActivityTools {

    public String getActivitySales(String activityId) {
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