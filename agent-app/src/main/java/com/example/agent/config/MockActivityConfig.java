package com.example.agent.config;

import com.example.agent.domain.activity.adapter.repository.IActivityRepository;
import com.example.agent.infrastructure.adapter.repository.mock.MockActivityRepository;
import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 只为使用活动工具的学习 Demo 装配模拟数据源。
 * 正式业务配置以后单独装配真实仓储，不会默认加载这里的模拟实现。
 */
@Configuration(proxyBeanMethods = false)
@Profile({
        "tool-demo",
        "console-demo",
        "console-loop-demo",
        "multi-tools-demo",
        "manual-agent-demo"
})

public class MockActivityConfig {

    @Bean
    public IActivityRepository activityRepository() {
        return new MockActivityRepository();
    }

    @Bean
    public ActivityTools activityTools(IActivityRepository activityRepository) {
        return new ActivityTools(activityRepository);
    }

    @Bean
    public ActivityInfoTools activityInfoTools(IActivityRepository activityRepository) {
        return new ActivityInfoTools(activityRepository);
    }
}
