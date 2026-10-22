package com.example.agent.demo;

import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
@Profile("console-demo")
public class QwenChatDemo3 implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ActivityTools tools;

    public QwenChatDemo3(ChatClient.Builder builder, ActivityTools tools) {
        this.chatClient = builder.build();
        this.tools = tools;
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("请输入问题，输入后按回车：");
        String question = scanner.nextLine();

        if (question.isBlank()) {
            System.out.println("问题不能为空，本次不调用模型。");
            return;
        }

        System.out.println("正在等待模型回答……");

        String answer = chatClient.prompt()
                .user(question)
                .tools(tools)
                .call()
                .content();

        System.out.println("模型回答：" + answer);
    }
}
