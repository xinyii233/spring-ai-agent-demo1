package com.example.agent.demo;

import com.example.agent.domain.activity.adapter.repository.IActivityRepository;
import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ManualAgentLoopBoundaryTest {

    @Test
    void stopsAtCallLimitWithoutExecutingLastTools(
            CapturedOutput output) {

        // 创建模型替身，不连接真实模型服务
        OpenAiChatModel model = mock(OpenAiChatModel.class);

        when(model.getOptions())
                .thenReturn(OpenAiChatOptions.builder().build());

        // 模拟模型每一次都要求调用销售查询工具
        AssistantMessage assistant = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall(
                                "call-test",
                                "function",
                                "getActivitySales",
                                "{\"activityId\":\"A001\"}")))
                .build();

        ChatResponse response =
                new ChatResponse(List.of(new Generation(assistant)));

        when(model.call(any(Prompt.class)))
                .thenReturn(response);

        // 仓储替身返回“未找到”，仍属于正常业务结果
        IActivityRepository repository =
                mock(IActivityRepository.class);

        when(repository.findSalesById("A001"))
                .thenReturn(Optional.empty());

        ManualAgentLoopDemo demo = new ManualAgentLoopDemo(
                model,
                new ActivityTools(repository),
                new ActivityInfoTools(repository));

        // 执行我们自己的真实循环
        demo.run();

        // 模型调用到第 4 次后停止
        verify(model, times(4)).call(any(Prompt.class));

        // 只有前 3 次执行工具，第 4 次不能再执行
        verify(repository, times(3)).findSalesById("A001");

        assertThat(output.getAll())
                .contains("MODEL_CALL_LIMIT")
                .doesNotContain("[任务结束] ANSWERED");
    }

    @Test
    void stopsWhenModelRequestFails(CapturedOutput output) {

        OpenAiChatModel model = mock(OpenAiChatModel.class);

        when(model.getOptions())
                .thenReturn(OpenAiChatOptions.builder().build());

        // 模拟模型调用抛出异常
        when(model.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("测试：模型调用失败"));

        IActivityRepository repository =
                mock(IActivityRepository.class);

        ManualAgentLoopDemo demo = new ManualAgentLoopDemo(
                model,
                new ActivityTools(repository),
                new ActivityInfoTools(repository));

        demo.run();

        // 只尝试一次，不继续进入下一轮
        verify(model, times(1)).call(any(Prompt.class));

        // 模型没有返回工具调用，因此不应发生任何仓储查询
        verifyNoInteractions(repository);

        assertThat(output.getAll())
                .contains("MODEL_REQUEST_FAILED")
                .doesNotContain("[任务结束] ANSWERED");
    }
    @Test
    void feedsToolFailureBackToModelAndContinues(CapturedOutput output) {
        // 模型替身按顺序返回工具请求和最终回答，不连接真实模型服务。
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.getOptions())
                .thenReturn(OpenAiChatOptions.builder().build());

        AssistantMessage toolRequest = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall(
                        "call-sales-failure",
                        "function",
                        "getActivitySales",
                        "{\"activityId\":\"A001\"}")))
                .build();
        ChatResponse firstResponse = new ChatResponse(
                List.of(new Generation(toolRequest)));

        AssistantMessage finalAnswer = AssistantMessage.builder()
                .content("""
                        {
                          "activityId": "A001",
                          "status": "QUERY_FAILED",
                          "message": "销售查询工具执行失败，本次无法提供销售数据。"
                        }
                        """)
                .build();
        ChatResponse secondResponse = new ChatResponse(List.of(new Generation(
                finalAnswer,
                ChatGenerationMetadata.builder().finishReason("stop").build())));
        when(model.call(any(Prompt.class)))
                .thenReturn(firstResponse, secondResponse);

        // 从仓储查询处制造异常，实际经过工具回调和 Agent 的异常处理。
        IActivityRepository repository = mock(IActivityRepository.class);
        when(repository.findSalesById("A001"))
                .thenThrow(new IllegalStateException("测试：模拟仓储查询失败"));

        ManualAgentLoopDemo demo = new ManualAgentLoopDemo(
                model,
                new ActivityTools(repository),
                new ActivityInfoTools(repository));
        demo.run();

        // 工具失败后还应调用一次模型，但不自动重试仓储查询。
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(promptCaptor.capture());
        verify(repository, times(1)).findSalesById("A001");

        // 第二次输入包含：系统消息、用户问题、助手工具请求、工具错误结果。
        Prompt secondPrompt = promptCaptor.getAllValues().get(1);
        assertThat(secondPrompt.getInstructions()).hasSize(4);
        assertThat(secondPrompt.getInstructions().get(2))
                .isInstanceOf(AssistantMessage.class);
        assertThat(secondPrompt.getInstructions().get(3))
                .isInstanceOf(ToolResponseMessage.class);

        ToolResponseMessage toolMessage =
                (ToolResponseMessage) secondPrompt.getInstructions().get(3);
        assertThat(toolMessage.getResponses()).hasSize(1);
        var toolResult = toolMessage.getResponses().get(0);
        assertThat(toolResult.id()).isEqualTo("call-sales-failure");
        assertThat(toolResult.name()).isEqualTo("getActivitySales");
        assertThat(toolResult.responseData())
                .contains("TOOL_EXECUTION_FAILED")
                .contains("本次没有获得业务数据");

        // ANSWERED 仅表示获得了回答，不表示销售查询成功。
        assertThat(output.getAll())
                .contains("[任务结束] ANSWERED")
                .contains("最终回答：销售查询工具执行失败")
                .doesNotContain("[任务结束] MODEL_REQUEST_FAILED")
                .doesNotContain("MODEL_CALL_LIMIT");
    }
    @ParameterizedTest
    @ValueSource(strings = {
            // 普通文本：不是 JSON。
            "查询成功",
            // JSON 语法正确，但状态不属于枚举。
            "{\"activityId\":\"A001\",\"status\":\"UNKNOWN\",\"message\":\"查询成功\"}",
            // 状态合法，但说明文字为空，触发 record 构造器检查。
            "{\"activityId\":\"A001\",\"status\":\"SUCCESS\",\"message\":\"\"}"
    })
    void rejectsInvalidFinalOutput(String invalidAnswer, CapturedOutput output) {
        OpenAiChatModel model = mock(OpenAiChatModel.class);
        when(model.getOptions())
                .thenReturn(OpenAiChatOptions.builder().build());

        // 模型正常结束，但内容不合格：应进入 INVALID_OUTPUT。
        AssistantMessage assistant = AssistantMessage.builder()
                .content(invalidAnswer)
                .build();
        ChatResponse response = new ChatResponse(List.of(new Generation(
                assistant,
                ChatGenerationMetadata.builder().finishReason("stop").build())));
        when(model.call(any(Prompt.class))).thenReturn(response);

        IActivityRepository repository = mock(IActivityRepository.class);
        ManualAgentLoopDemo demo = new ManualAgentLoopDemo(
                model,
                new ActivityTools(repository),
                new ActivityInfoTools(repository));
        demo.run();

        // 输出不合法后停止，不重试模型，也不执行任何工具。
        verify(model, times(1)).call(any(Prompt.class));
        verifyNoInteractions(repository);
        assertThat(output.getAll())
                .contains("[任务结束] INVALID_OUTPUT")
                .doesNotContain("[任务结束] ANSWERED");
    }
}
