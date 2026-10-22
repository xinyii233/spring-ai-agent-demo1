package com.example.agent.demo;

import com.example.agent.trigger.tool.ActivityInfoTools;
import com.example.agent.trigger.tool.ActivityTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Scanner;

@Component
@Profile("multi-tools-demo")
public class QwenChatDemo7 implements CommandLineRunner {

    private final ChatClient chatClient;
    private final ActivityTools salesTools;
    private final ActivityInfoTools infoTools;

    public QwenChatDemo7(ChatClient.Builder builder,
                        ActivityTools salesTools,
                        ActivityInfoTools infoTools) {
        // 工具由 app 配置装配，Demo 只负责把能力提供给模型。
        this.salesTools = salesTools;
        this.infoTools = infoTools;
        this.chatClient = builder
                .defaultSystem("""
                        你是一名活动业务查询助手。
                        请使用中文回答。
                        涉及具体活动的数据时，请使用相应工具查询，
                        不要自行编造活动数据。
                        工具未查到时，请明确说明未找到。
                        """)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        // 复用构造方法注入的两个工具，不在 Demo 中保存模拟业务数据。

        System.out.println("请输入活动查询问题，输入 exit 结束。");

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

            System.out.println("正在等待模型回答……");

            try {
                String answer = chatClient.prompt()
                        .user(question)
                        // 同时向模型提供两个工具对象中的工具定义
                        .tools(salesTools, infoTools)
                        .call()
                        .content();

                System.out.println("模型：" + answer);

            } catch (Exception e) {
                System.err.println(
                        "本次请求失败，异常类型："
                                + e.getClass().getSimpleName());
            }
        }

        // 不关闭共享的 System.in
    }
}
