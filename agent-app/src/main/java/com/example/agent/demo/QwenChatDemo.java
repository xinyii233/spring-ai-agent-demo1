package com.example.agent.demo;

import org.springframework.context.annotation.Profile;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Profile("chat-demo")
public class QwenChatDemo implements CommandLineRunner {

    private final ChatClient chatClient;

    public QwenChatDemo(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public void run(String... args) {
        String question = "请用一句中文解释什么是大语言模型。";

        System.out.println("发送问题：" + question);

        String answer = chatClient.prompt()
                .user(question)
                .call()
                .content();

        System.out.println("模型回答：" + answer);
    }
}