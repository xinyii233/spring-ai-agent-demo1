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
3. 启动入口位于 agent-app 的 `com.example.agent.SpringAiAgentDemo1Application`。调用模型时，在 IDEA 运行配置设置 `DASHSCOPE_API_KEY` 环境变量，并选择一个 Demo Profile。
4. 不调用模型的普通 Java 演示位于 agent-app 的 `com.example.agent.demo.ActivityToolsLocalDemo`，可以直接运行其 main 方法。工具类本身不再承担 main 演示入口。

## 学习模拟数据与正式实现的边界

工具使用 `@Tool` 声明能力，固定数据集中到模拟仓储中，目录按现有六模块划分：

```text
agent-domain
  activity/model/ActivityInfo.java                活动信息类型
  activity/model/ActivitySales.java               累计支付数据类型
  activity/adapter/repository/IActivityRepository.java  查询接口
agent-infrastructure
  adapter/repository/mock/MockActivityRepository.java   固定模拟数据和接口实现
agent-trigger
  tool/ActivityTools.java                         销售查询工具入口
  tool/ActivityInfoTools.java                     活动信息工具入口
agent-app
  config/MockActivityConfig.java                  学习环境的组件装配
  demo/ActivityToolsLocalDemo.java                无模型调用的本地演示
  src/test/java/...                              自动化测试
```

调用方向：Demo → 工具 → `IActivityRepository` → app 选择的实现。trigger 只依赖 domain 中的接口，不依赖 infrastructure。这里先做最小查询分离，业务规则与领域服务后续逐步引入。

`MockActivityConfig` 只在 `tool-demo`、`console-demo`、`console-loop-demo`、`multi-tools-demo` 中启用，普通启动或仅启用 `prod` 不会装配模拟仓储。正式环境不要同时开启这些学习 Profile。以后增加数据库或远程接口仓储实现时，由正式配置装配并提供工具 Bean。

| Demo | IDEA 有效配置文件 |
| --- | --- |
| QwenChatDemo | chat-demo |
| QwenChatDemo2 | tool-demo |
| QwenChatDemo3 | console-demo |
| QwenChatDemo4 | console-loop-demo |
| QwenChatDemo5 | manual-memory-demo |
| QwenChatDemo6 | framework-memory-demo |
| QwenChatDemo7 | multi-tools-demo |

模拟 A001 为 2026-10-01 至 2026-10-21 的已结束活动，累计支付成功订单 120 单，退款前销售额 9600 元。这是固定历史样本，不随系统日期变化；退款数据尚未实现。

模拟实现放在 `src/main/java` 的 `mock` 包，以便正常启动的学习 Demo 使用；`src/test/java` 用于验证行为的自动化测试，不作为运行中 Demo 的数据来源。当前没有增加新模块或数据库依赖。

根目录中的 group-buy-market 是参考项目，没有加入当前 Maven 聚合构建。
