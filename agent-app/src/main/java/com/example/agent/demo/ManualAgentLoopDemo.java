package com.example.agent.demo;

import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
//import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("manual-agent-demo")
public class ManualAgentLoopDemo implements CommandLineRunner {

    // 最多调用模型 4 次，包括生成最终回答的那一次
    private static final int MAX_MODEL_CALLS = 4;

    private final OpenAiChatModel chatModel;
    private final ToolCallback[] toolCallbacks;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

//    boolean simulateToolFailure = true;
    private boolean simulateToolFailure = false;
    public ManualAgentLoopDemo(
            OpenAiChatModel chatModel,
            ActivityTools salesTools,
            ActivityInfoTools infoTools) {

        // 使用当前项目的 OpenAI 兼容模型适配器，不经过 ChatClient 的自动工具循环。
        this.chatModel = chatModel;

        // 根据已有 @Tool 方法生成工具回调及其 Schema
        this.toolCallbacks = ToolCallbacks.from(salesTools, infoTools);
    }

    @Override
    public void run(String... args) {

        // 工具注册表：根据模型返回的名称找到允许执行的工具
        Map<String, ToolCallback> toolRegistry = new HashMap<>();

        for (ToolCallback callback : toolCallbacks) {
            String name = callback.getToolDefinition().name();
            toolRegistry.put(name, callback);
        }

        // 这些消息只属于当前问题内部的执行过程
        List<Message> messages = new ArrayList<>();

        // 从应用的 classpath 中找到提示词文件
        ClassPathResource promptResource =
                new ClassPathResource("prompts/activity-agent-system.st");

        // 使用 Spring AI 创建系统提示词模板
        SystemPromptTemplate systemTemplate =
                new SystemPromptTemplate(promptResource);

//        // 填充变量，生成一条系统消息，并加入当前任务的消息列表
//        messages.add(systemTemplate.createMessage(Map.of(
//                "assistantName", "活动运营查询助手",
//                "language", "中文"
//        )));

        // 这段字符串作为变量值填入模板，不会被再次当成模板解析
        String outputFormat = """
        只返回一个 JSON 对象，不要输出 Markdown 代码块或额外解释。

        对象包含三个字段：
        - activityId：字符串；无法确定活动编号时为 null。
        - status：只能使用下面规定的状态。
        - message：非空字符串，向用户说明查询结果或需要补充的信息。

        status 的含义：
        SUCCESS：已经获得用户需要的数据。
        NEED_INPUT：缺少必要信息，需要用户补充。
        NOT_FOUND：工具明确返回未找到活动。
        PARTIAL：只获得了用户需要的部分信息。
        QUERY_FAILED：工具执行失败，未获得所需数据。

        缺少活动编号时的格式示例：
        {
          "activityId": null,
          "status": "NEED_INPUT",
          "message": "请提供您要查询的活动编号。"
        }
        """;

        messages.add(systemTemplate.createMessage(Map.of(
                "assistantName", "活动运营查询助手",
                "language", "中文",
                "outputFormat", outputFormat
        )));


        String question = "查询 A001 的销售数据。";
        messages.add(new UserMessage(question));

        // 向模型提供可用工具，不在这里指定必须调用哪个
        // 从已配置的模型选项复制，只追加工具，保留千问模型名、地址和密钥。
        // 不直接 new 通用选项，也不创建带有其他默认模型名的空白选项。
//        OpenAiChatOptions options = chatModel.getOptions().mutate()
//                .toolCallbacks(toolCallbacks)
//                .build();
        // 读取最终回答的结构定义
        String answerSchema;

        try {
            answerSchema = new ClassPathResource(
                    "activity-agent-answer.schema.json"
            ).getContentAsString(StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException("读取回答 Schema 失败", e);
        }

        // 声明：最终回答采用 JSON Schema 模式，并启用严格约束
        OpenAiChatModel.ResponseFormat responseFormat =
                OpenAiChatModel.ResponseFormat.builder()
                        .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(answerSchema)
                        .strict(true)
                        .build();

        // 保留原来的模型配置和工具，同时增加最终回答格式约束
        OpenAiChatOptions options = chatModel.getOptions().mutate()
                .toolCallbacks(toolCallbacks)
                .responseFormat(responseFormat)
                .build();
        System.out.println("用户问题：" + question);

        for (int step = 1; step <= MAX_MODEL_CALLS; step++) {

            System.out.println("\n===== 第 " + step + " 次模型调用 =====");
            System.out.println("当前请求消息数：" + messages.size());

            // 本行只取得本次模型响应，不会替我们执行工具
//            ChatResponse response =
//                    chatModel.call(new Prompt(messages, options));
            ChatResponse response;

            try {
                response = chatModel.call(new Prompt(messages, options));

            } catch (RuntimeException e) {
                System.err.println(
                        "[任务结束] MODEL_REQUEST_FAILED"
                                + "，第 " + step + " 次模型调用失败"
                                + "，异常类型：" + e.getClass().getSimpleName());
                return;
            }

//            if (response == null || response.getResult() == null) {
//                System.err.println("模型没有返回可用结果，停止执行。");
//                return;
//            }
//
//            AssistantMessage assistant =
//                    response.getResult().getOutput();
//
//            // 保存完整助手消息，包括工具调用编号、参数等信息
//            messages.add(assistant);
//
//            // 没有工具调用请求：打印回答并结束整个循环
//            if (!assistant.hasToolCalls()) {
//                System.out.println("最终回答：" + assistant.getText());
//                return;
//            }
//
//            System.out.println(
//                    "模型请求的工具数量：" + assistant.getToolCalls().size());
            // 没有响应或没有生成结果，不能作为成功回答
            if (response == null || response.getResult() == null) {
                System.err.println("[任务结束] EMPTY_RESPONSE：模型没有返回可用结果。");
                return;
            }

            AssistantMessage assistant = response.getResult().getOutput();

            if (assistant == null) {
                System.err.println("[任务结束] EMPTY_RESPONSE：缺少助手消息。");
                return;
            }

            // 没有工具调用时，检查是不是正常完成的文本回答
            if (!assistant.hasToolCalls()) {
                String finishReason =
                        response.getResult().getMetadata().getFinishReason();

                if (!"stop".equalsIgnoreCase(finishReason)) {
                    System.err.println(
                            "[任务结束] INCOMPLETE_RESPONSE"
                                    + "，模型结束原因：" + finishReason);
                    return;
                }

                String answer = assistant.getText();

                if (answer == null || answer.isBlank()) {
                    System.err.println("[任务结束] EMPTY_ANSWER：模型没有返回回答文本。");
                    return;
                }

//                // ANSWERED 表示获得了回答，不保证其中每个业务目标都已完成
//                System.out.println("[任务结束] ANSWERED");
//                System.out.println("最终回答：" + answer);
//                return;
                System.out.println("模型原始回答：" + answer);

                ActivityAgentAnswer parsedAnswer;

                try {
                    // 将模型返回的 JSON 字符串转换成 Java 对象
                    parsedAnswer = jsonMapper.readValue(
                            answer,
                            ActivityAgentAnswer.class);

                    if (parsedAnswer == null) {
                        throw new IllegalArgumentException("最终回答不能为 null");
                    }

                } catch (RuntimeException e) {
                    System.err.println(
                            "[任务结束] INVALID_OUTPUT"
                                    + "：回答无法解析或字段校验失败"
                                    + "，异常类型：" + e.getClass().getSimpleName());
                    return;
                }

                System.out.println("[任务结束] ANSWERED");
                System.out.println("活动编号：" + parsedAnswer.activityId());
                System.out.println("业务状态：" + parsedAnswer.status());
                System.out.println("最终回答：" + parsedAnswer.message());
                return;
            }

            // 有工具请求，但已经没有下一次模型调用额度：不再执行工具
            if (step >= MAX_MODEL_CALLS) {
                System.err.println(
                        "[任务结束] MODEL_CALL_LIMIT"
                                + "：已达到模型调用上限，"
                                + "本次工具请求不再执行，尚未获得最终回答。");
                return;
            }

            // 有继续执行的额度，才保存工具调用消息并进入工具执行流程
            messages.add(assistant);

            System.out.println(
                    "模型请求的工具数量：" + assistant.getToolCalls().size());
            // 收集这次响应中所有工具的执行结果
            List<ToolResponseMessage.ToolResponse> toolResults =
                    new ArrayList<>();

            for (AssistantMessage.ToolCall toolCall
                    : assistant.getToolCalls()) {

                System.out.println("工具名称：" + toolCall.name());
                System.out.println("工具参数：" + toolCall.arguments());

                // 只从我们注册的工具中查找，不允许任意执行其他方法
                ToolCallback callback = toolRegistry.get(toolCall.name());

                if (callback == null) {
                    System.err.println("未知工具，停止执行：" + toolCall.name());
                    return;
                }

                // 显式执行工具；框架负责将 JSON 参数转换成 Java 参数
//              String result = callback.call(toolCall.arguments());

                String result;
//                try {
//                    result = callback.call(toolCall.arguments());
//
//                }
                                try {
                    if (simulateToolFailure
                            && "getActivitySales".equals(toolCall.name())) {

                        simulateToolFailure = false;
                        throw new IllegalStateException("学习测试：模拟销售查询失败");
                    }

                    result = callback.call(toolCall.arguments());

                }catch (Exception e) {
                    // 详细排查信息留在本地
                    System.err.println(
                            "工具执行失败：" + toolCall.name()
                                    + "，异常类型：" + e.getClass().getSimpleName());

                    // 给模型一个明确的失败结果，不把异常冒充查询数据
                    result = """
                    {
                      "status": "error",
                      "code": "TOOL_EXECUTION_FAILED",
                      "message": "工具执行失败，本次没有获得业务数据。"
                    }
                    """;
                }

                System.out.println("工具结果：" + result);

                // 把结果与模型发出的工具调用编号关联起来
                toolResults.add(new ToolResponseMessage.ToolResponse(
                        toolCall.id(),
                        toolCall.name(),
                        result));
            }

            // 保存工具结果；下一轮循环会把它们发送给模型
            messages.add(ToolResponseMessage.builder()
                    .responses(toolResults)
                    .build());
        }

        System.err.println(
                "已达到最大模型调用次数，停止执行，尚未获得最终回答。");
    }
}
