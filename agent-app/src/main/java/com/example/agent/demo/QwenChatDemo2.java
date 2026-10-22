package com.example.agent.demo;

import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("tool-demo")
public class QwenChatDemo2 implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ActivityTools tools;

    public QwenChatDemo2(ChatClient.Builder builder, ActivityTools tools) {
        this.chatClient = builder.build();
        this.tools = tools;
    }

    @Override
    public void run(String... args) {
        String question = "请查询 A001 活动的销售数据，并根据查询结果回答，不要编造。然后用一句话解释什么是 Java 接口。";

        System.out.println("发送问题：" + question);

        String answer = chatClient.prompt()
                .user(question)
                .tools(tools)
                .call()
                .content();

        System.out.println("模型回答：" + answer);
    }
}
