package com.example.agent.demo;

import com.example.agent.infrastructure.adapter.repository.mock.MockActivityRepository;
import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ManualAgentLoopDemoTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void runsWithActualOpenAiAdapterAndStopsAfterFinalAnswer(
            boolean requestTools, CapturedOutput output) throws Exception {
        // 使用实际 OpenAiChatModel 和本地 HTTP 接口，覆盖运行时选项类型转换。
        // true：请求两个工具后回答；false：直接回答，不执行工具。
        ObjectMapper mapper = new ObjectMapper();
        List<String> requests = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean toolRound = requestTools && requests.size() == 1;
            Map<String, Object> message = toolRound
                    ? Map.of("role", "assistant", "content", "", "tool_calls", List.of(
                            Map.of("id", "call_sales", "type", "function", "function", Map.of(
                                    "name", "getActivitySales", "arguments", "{\"activityId\":\"A001\"}")),
                            Map.of("id", "call_info", "type", "function", "function", Map.of(
                                    "name", "getActivityInfo", "arguments", "{\"activityId\":\"A001\"}"))))
                    : Map.of("role", "assistant", "content", """
                            {
                              "activityId": "A001",
                              "status": "SUCCESS",
                              "message": "本地测试最终回答"
                            }
                            """);
            byte[] response = mapper.writeValueAsBytes(Map.of(
                    "id", "local-response-" + requests.size(),
                    "object", "chat.completion", "created", 1, "model", "local-test-model",
                    "choices", List.of(Map.of("index", 0,
                            "finish_reason", toolRound ? "tool_calls" : "stop",
                            "message", message))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(response);
            }
        });

        server.start();
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                            .apiKey("local-test-credential")
                            .model("local-test-model")
                            .maxRetries(0)
                            .build())
                    .build();
            MockActivityRepository repository = new MockActivityRepository();
            ManualAgentLoopDemo demo = new ManualAgentLoopDemo(model,
                    new ActivityTools(repository), new ActivityInfoTools(repository));

            demo.run();

            // 不仅检查请求次数，也确认最终 JSON 被成功解析和接受。
            assertThat(output.getAll())
                    .contains("[任务结束] ANSWERED")
                    .contains("最终回答：本地测试最终回答")
                    .doesNotContain("INVALID_OUTPUT");
            assertThat(requests).hasSize(requestTools ? 2 : 1);
            var firstRequest = mapper.readTree(requests.get(0));
            assertThat(firstRequest.path("model").asText()).isEqualTo("local-test-model");
            assertThat(firstRequest.path("messages").size()).isEqualTo(2);
            assertThat(firstRequest.path("tools").size()).isEqualTo(2);
            if (requestTools) {
                var messages = mapper.readTree(requests.get(1)).path("messages");
                assertThat(messages.size()).isEqualTo(5);
                assertThat(messages.get(2).path("role").asText()).isEqualTo("assistant");
                assertThat(messages.get(2).path("tool_calls").size()).isEqualTo(2);
                assertThat(messages.get(3).path("tool_call_id").asText()).isEqualTo("call_sales");
                assertThat(messages.get(3).path("content").asText()).contains("9600");
                assertThat(messages.get(4).path("tool_call_id").asText()).isEqualTo("call_info");
                assertThat(messages.get(4).path("content").asText()).contains("已结束");
            }
        } finally {
            server.stop(0);
        }
    }
}
