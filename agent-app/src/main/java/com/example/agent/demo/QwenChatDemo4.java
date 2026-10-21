package com.example.agent.demo;

import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
@Profile("console-loop-demo")
public class QwenChatDemo4 implements CommandLineRunner {

    private final ChatClient chatClient;

//    public QwenChatDemo4(ChatClient.Builder builder) {
//        this.chatClient = builder.build();
//    }
    public QwenChatDemo4(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
}

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);
        ActivityTools tools = new ActivityTools();

        System.out.println("请输入问题，输入 exit 结束本次交互。");

        while (true) {
            System.out.print("你：");

            // 输入流结束时退出循环
            if (!scanner.hasNextLine()) {
                break;
            }

            String question = scanner.nextLine().trim();

            // exit 只结束输入循环，不关闭 Spring Boot 应用
            if ("exit".equalsIgnoreCase(question)) {
                System.out.println("已结束控制台交互。");
                break;
            }

            // 空输入不调用模型，直接进入下一轮
            if (question.isBlank()) {
                System.out.println("问题不能为空，请重新输入。");
                continue;
            }

            System.out.println("正在等待模型回答……");

            // 在循环内部处理异常，本轮失败后仍能继续输入
            try {
                String answer = chatClient.prompt()
                        .user(question)
                        .tools(tools)
                        .call()
                        .content();

                System.out.println("模型：" + answer);
            } catch (Exception e) {
                System.err.println(
                        "本次请求失败，异常类型："
                                + e.getClass().getSimpleName());
                System.err.println(
                        "可能涉及网络、认证、模型配置或工具执行问题，请排查后重试。");
            }
        }

        // 不关闭 scanner，避免关闭共享的 System.in
    }
}