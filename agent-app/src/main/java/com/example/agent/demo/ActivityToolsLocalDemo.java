package com.example.agent.demo;

import com.example.agent.infrastructure.adapter.repository.mock.MockActivityRepository;
import com.example.agent.trigger.tool.ActivityTools;

/** 普通 Java 本地演示，不启动 Spring Boot，不需要模型密钥，也不调用大模型。 */
public class ActivityToolsLocalDemo {

    public static void main(String[] args) {
        // app 负责选择并组装实现，trigger 不依赖 infrastructure。
        ActivityTools tools = new ActivityTools(new MockActivityRepository());
        System.out.println(tools.getActivitySales("A001"));
        System.out.println(tools.getActivitySales("A999"));
    }
}
