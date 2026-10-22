package com.example.agent.demo;

/**
 * 学习用：模型最终回答的数据结构。
 * 后续整理正式接口时，再放入相应的响应模型位置。
 */
public record ActivityAgentAnswer(
        String activityId,
        Status status,
        String message
    ) {

    public enum Status {
        SUCCESS,      // 已获得用户需要的数据
        NEED_INPUT,   // 需要用户补充信息
        NOT_FOUND,    // 工具明确返回未找到
        PARTIAL,      // 仅获得部分信息
        QUERY_FAILED  // 工具执行失败，未获得所需数据
    }

    // 紧凑构造器：创建对象时检查必要字段
    public ActivityAgentAnswer {
        if (status == null) {
            throw new IllegalArgumentException("status 不能为空");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message 不能为空");
        }
    }
}