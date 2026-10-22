package com.example.agent.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

@Component
@Profile("manual-memory-demo")
public class QwenChatDemo5 implements CommandLineRunner {


    // 最多保留最近 2 轮成功问答；一轮包括用户问题和模型回答
    private static final int MAX_HISTORY_TURNS = 2;

    private final ChatClient chatClient;

    public QwenChatDemo5(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        // 保存本次控制台交互的聊天记录
        List<Message> history = new ArrayList<>();

        // 系统提示词：告诉模型如何回答
        history.add(new SystemMessage("""
                你是一名 Java 和 AI Agent 学习助手。
                请使用中文，用适合初学者的方式解释问题。
                根据提供的聊天记录回答；没有相关信息时，请直接说明不知道。
                """));

        System.out.println("请输入问题，输入 exit 结束本次交互。");

        while (true) {
            System.out.print("你：");

            if (!scanner.hasNextLine()) {
                break;
            }

            String question = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(question)) {
                System.out.println("已结束控制台交互。");
                break;
            }

            if (question.isBlank()) {
                System.out.println("问题不能为空，请重新输入。");
                continue;
            }

            // 复制历史，再加入当前问题，组成这一轮请求
            List<Message> requestMessages = new ArrayList<>(history);
            requestMessages.add(new UserMessage(question));

            System.out.println("正在等待模型回答……");

            try {
                String answer = chatClient.prompt()
                        .messages(requestMessages)
                        .call()
                        .content();

                if (answer == null || answer.isBlank()) {
                    System.out.println("模型未返回文本，本轮不保存到历史。");
                    continue;
                }

//                // 请求成功后，保存这一轮问题与回答
//                history.add(new UserMessage(question));
//                history.add(new AssistantMessage(answer));
//
//                System.out.println("模型：" + answer);
//                System.out.println("当前保存的消息数：" + history.size());

                // 请求成功后，保存这一轮问题与回答
                history.add(new UserMessage(question));
                history.add(new AssistantMessage(answer));
                // 1 条系统消息 + 每轮 2 条问答消息
                int maxHistoryMessages = 1 + MAX_HISTORY_TURNS * 2;
                // 超过窗口上限时，删除最早的一整轮问答
                while (history.size() > maxHistoryMessages) {
                    // 索引 0 是系统消息，始终保留
                    // subList(1, 3) 选中索引 1 和 2，不包含索引 3
                    // clear() 会从原来的 history 中删除这两条消息
                    history.subList(1, 3).clear();
                }
                System.out.println("模型：" + answer);
                System.out.println("当前保存的消息数：" + history.size());
                System.out.println("当前保留的问答轮数：" + (history.size() - 1) / 2);


            } catch (Exception e) {
                System.err.println(
                        "本次请求失败，异常类型："
                                + e.getClass().getSimpleName());
                System.err.println("本轮未保存到历史，可以重新输入。");
            }
        }

        // 不关闭 scanner，避免关闭共享的 System.in
    }
}