package com.example.agent.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.agent.trigger.tool.ActivityTools;
import com.example.agent.infrastructure.adapter.repository.mock.MockActivityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiHttpLoggingConfigTest {

    @Test
    void logsActualBodiesForBothToolRequestsWithoutExposingCredentials() throws Exception {
        // 本地模拟模型接口：第一轮要求调用工具，第二轮返回最终回答，不访问外部服务。
        List<String> receivedBodies = new ArrayList<>();
        List<String> receivedAuthorization = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            receivedAuthorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String choice = receivedBodies.size() == 1 ? """
                    {"finish_reason":"tool_calls","index":0,"message":{"role":"assistant",
                    "content":null,"tool_calls":[{"id":"call_local_1","type":"function",
                    "function":{"name":"getActivitySales","arguments":"{\\"activityId\\":\\"A001\\"}"}}]}}
                    """ : """
                    {"finish_reason":"stop","index":0,
                    "message":{"role":"assistant","content":"A001 的销售额为9600元"}}
                    """;
            String response = "{\"id\":\"local-test\",\"object\":\"chat.completion\","
                    + "\"created\":1,\"model\":\"local-test-model\",\"choices\":[" + choice + "]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var stream = exchange.getResponseBody()) {
                stream.write(bytes);
            }
        });

        Logger logger = (Logger) LoggerFactory.getLogger(OpenAiHttpLoggingConfig.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        server.start();
        try {
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .apiKey("local-test-credential")
                            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                            .model("local-test-model")
                            .build())
                    .httpClientBuilderCustomizer(new OpenAiHttpLoggingConfig().demo4HttpLoggingCustomizer())
                    .build();
            // 测试中手动装配；应用正常启动时由 Spring Boot 提供工具调用 Advisor。
            ChatClient client = ChatClient.builder(model)
                    .defaultAdvisors(ToolCallingAdvisor.builder().build())
                    .build();

            String answer = client.prompt().user("A001")
                    .tools(new ActivityTools(new MockActivityRepository()))
                    .call().content();

            assertThat(answer).isEqualTo("A001 的销售额为9600元");
            assertThat(receivedBodies).hasSize(2);
            assertThat(receivedAuthorization).containsOnly("Bearer local-test-credential");

            ObjectMapper mapper = new ObjectMapper();
            var firstRequest = mapper.readTree(receivedBodies.get(0));
            assertThat(firstRequest.path("messages").get(0).path("content").asText()).isEqualTo("A001");
            assertThat(firstRequest.path("tools").get(0).path("function").path("name").asText())
                    .isEqualTo("getActivitySales");
            assertThat(receivedBodies.get(1)).contains("9600", "call_local_1");

            String logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
            // 确认日志中的 JSON 与服务端实际接收的一致，且不会泄露认证头。
            assertThat(logs).contains(receivedBodies.get(0), receivedBodies.get(1), "[已隐藏]")
                    .doesNotContain("local-test-credential");
        } finally {
            server.stop(0);
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
