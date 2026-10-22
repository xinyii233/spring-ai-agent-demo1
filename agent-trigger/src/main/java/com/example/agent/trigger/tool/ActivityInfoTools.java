package com.example.agent.trigger.tool;

import com.example.agent.domain.activity.adapter.repository.IActivityRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.format.DateTimeFormatter;

public class ActivityInfoTools {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IActivityRepository activityRepository;

    public ActivityInfoTools(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    // 描述要与销售查询区分：这里查询状态和时间，不查询销售额
    @Tool(description = """
            根据活动编号查询活动基本信息，
            返回活动名称、活动状态、开始时间和结束时间。
            不提供订单数和销售额。
            """)
    public String getActivityInfo(
            @ToolParam(description = "活动编号，例如 A001")
            String activityId) {

        // 本地执行日志：用于确认模型是否真正请求了这个工具
        System.out.println(
                "[工具执行] getActivityInfo，参数：" + activityId);

        // 数据来源由接口实现决定；工具只格式化模型需要的查询结果。
        return activityRepository.findInfoById(activityId)
                .map(info -> """
                        活动编号：%s
                        活动名称：%s
                        活动状态：%s
                        开始时间：%s
                        结束时间：%s
                        """.formatted(info.activityId(), info.activityName(), info.status(),
                        info.startTime().format(TIME_FORMAT), info.endTime().format(TIME_FORMAT)))
                .orElse("未找到活动：" + activityId);
    }
}
