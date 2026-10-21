# Spring AI Agent 学习项目

本项目采用单体部署、六个 Maven 模块的 DDD 分层结构。

| 模块 | 职责 |
| --- | --- |
| agent-app | Spring Boot 启动入口、配置、组件装配和集成测试 |
| agent-api | 对外接口契约、请求 DTO 和响应对象 |
| agent-trigger | HTTP、模型工具调用等外部入口 |
| agent-domain | 业务模型、领域服务和仓储接口 |
| agent-infrastructure | 模型客户端、仓储实现及外部系统访问 |
| agent-types | 公共异常、枚举等基础类型 |

内部依赖：app → trigger、infrastructure；trigger → api、domain、types；infrastructure → domain；domain → types。api、types 不依赖其他业务模块。

app 是启动与装配模块，并非独立的 application 应用层。本阶段不添加第七个模块。业务用例逐步实现，暂不预设复杂领域模型。

## IDEA 使用

1. 在项目根 pom.xml 上选择 Maven → Reload Project，或在 Maven 工具窗口选择 Reload All Maven Projects。
2. 使用 Java 21，在根目录执行 `mvn compile`，验证全部模块编译。
3. 启动入口位于 agent-app 的 `com.example.agent.SpringAiAgentDemo1Application`。当前尚未配置并验证模型访问，编译成功不等于应用启动成功。
4. 独立的模拟查询示例位于 agent-trigger 的 `com.example.agent.trigger.tool.ActivityTools`，可以直接运行其 main 方法。

当前 ActivityTools 保留最初学习案例，尚未注册为 @Tool，也尚未拆分仓储实现。后续逐步完成这些工作。

根目录中的 group-buy-market 是参考项目，没有加入当前 Maven 聚合构建。
