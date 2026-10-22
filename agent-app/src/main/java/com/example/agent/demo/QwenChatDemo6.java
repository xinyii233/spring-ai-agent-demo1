package com.example.agent.demo;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
@Profile("framework-memory-demo")
public class QwenChatDemo6 implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public QwenChatDemo6(ChatClient.Builder builder) {

        // 存储位置：当前 Java 进程的内存，不是数据库
        InMemoryChatMemoryRepository repository =
                new InMemoryChatMemoryRepository();

        // 管理策略：每个会话最多保存 10 条消息
        this.chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();

        this.chatClient = builder
                // 每次请求使用的系统提示词
                .defaultSystem("""
                        你是一名 Java 和 AI Agent 学习助手。
                        请使用中文，用适合初学者的方式解释问题。
                        涉及用户姓名、学习方向等个人信息时，
                        请根据提供的聊天记录回答；
                        没有记录就说明不知道，不要猜测。
                        """)
                .defaultAdvisors(
                        // 自动读取历史、补充上下文并保存消息
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),

                        // 输出请求与响应对象，便于观察历史消息
                        new SimpleLoggerAdvisor()
                )
                .build();
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        // 默认使用会话 A；也可以在运行过程中切换
        String conversationId = "demo6-a";

        System.out.println("输入问题开始聊天。");
        System.out.println("/a 切换会话 A，/b 切换会话 B。");
        System.out.println("exit 结束控制台交互。");

        while (true) {
            System.out.print("你[" + conversationId + "]：");

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

            // 本地处理切换命令，不把命令发送给模型
            if ("/a".equalsIgnoreCase(question)) {
                conversationId = "demo6-a";
                System.out.println("已切换到会话 A。");
                continue;
            }

            if ("/b".equalsIgnoreCase(question)) {
                conversationId = "demo6-b";
                System.out.println("已切换到会话 B。");
                continue;
            }

            // 为本轮请求确定会话编号，供下面的 lambda 使用
            String currentConversationId = conversationId;

            System.out.println("正在等待模型回答……");

            try {
                String answer = chatClient.prompt()
                        .user(question)
                        // 指定本轮读写哪个会话的记忆
                        .advisors(a -> a.param(
                                ChatMemory.CONVERSATION_ID,
                                currentConversationId))
                        .call()
                        .content();

                if (answer == null || answer.isBlank()) {
                    System.out.println("模型未返回文本。");
                } else {
                    System.out.println("模型：" + answer);
                }

                System.out.println(
                        "当前会话保存的消息数："
                                + chatMemory.get(currentConversationId).size());

            } catch (Exception e) {
                System.err.println(
                        "本次请求失败，异常类型："
                                + e.getClass().getSimpleName());
            }
        }

        // 不关闭 scanner，避免关闭共享的 System.in
    }
}