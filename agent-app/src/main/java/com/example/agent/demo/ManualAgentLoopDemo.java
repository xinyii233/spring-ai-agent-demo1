package com.example.agent.demo;

import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
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

        messages.add(new SystemMessage("""
                你是一名活动查询助手。
                请使用中文回答。
                具体活动数据必须通过提供的工具查询，不要编造。
                信息足够后直接回答，不要重复查询。
                """));

        String question = "查询 A001 的活动信息和销售数据。";
        messages.add(new UserMessage(question));

        // 向模型提供可用工具，不在这里指定必须调用哪个
        // 从已配置的模型选项复制，只追加工具，保留千问模型名、地址和密钥。
        // 不直接 new 通用选项，也不创建带有其他默认模型名的空白选项。
        OpenAiChatOptions options = chatModel.getOptions().mutate()
                .toolCallbacks(toolCallbacks)
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

                // ANSWERED 表示获得了回答，不保证其中每个业务目标都已完成
                System.out.println("[任务结束] ANSWERED");
                System.out.println("最终回答：" + answer);
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
