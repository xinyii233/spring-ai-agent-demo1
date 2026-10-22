package com.example.agent.config;

import com.example.agent.domain.activity.adapter.repository.IActivityRepository;
import com.example.agent.infrastructure.adapter.repository.mock.MockActivityRepository;
import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MockActivityConfigTest {

    @Test
    void learningProfilesUseMockRepositoryAndExposeWorkingTools() {
        // 只启动工具装配，不启动控制台 Runner、不访问模型或数据库。
        for (String profile : new String[]{
                "tool-demo", "console-demo", "console-loop-demo", "multi-tools-demo"}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.register(MockActivityConfig.class);
                context.refresh();

                assertThat(context.getBean(IActivityRepository.class))
                        .isInstanceOf(MockActivityRepository.class);

                ActivityTools salesTools = context.getBean(ActivityTools.class);
                ActivityInfoTools infoTools = context.getBean(ActivityInfoTools.class);
                assertThat(salesTools.getActivitySales("A001"))
                        .contains("120", "9600", "扣除退款前");
                assertThat(infoTools.getActivityInfo("A001"))
                        .contains("已结束", "2026-10-21 23:59:59");
                assertThat(salesTools.getActivitySales("A999")).isEqualTo("未找到活动：A999");
                assertThat(infoTools.getActivityInfo("A999")).isEqualTo("未找到活动：A999");
            }
        }
    }

    @Test
    void normalAndProductionProfilesDoNotRegisterMockData() {
        for (String profile : new String[]{"default", "prod"}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles(profile);
                context.register(MockActivityConfig.class);
                context.refresh();

                assertThat(context.getBeansOfType(IActivityRepository.class)).isEmpty();
                assertThat(context.getBeansOfType(ActivityTools.class)).isEmpty();
                assertThat(context.getBeansOfType(ActivityInfoTools.class)).isEmpty();
            }
        }
    }
}
