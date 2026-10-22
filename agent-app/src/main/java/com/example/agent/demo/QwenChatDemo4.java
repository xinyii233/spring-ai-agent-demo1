// 声明当前类所属的包；用于组织类，并与其他包中的同名类区分。
package com.example.agent.demo;

// 引入 Spring AI 的日志 Advisor，用于记录 ChatClient 层的请求和响应对象。
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

// 引入我们自己编写的工具类，里面有 getActivitySales 活动销售查询方法。
import com.example.agent.trigger.tool.ActivityTools;
// 引入对话客户端；通过它组织消息、提供工具并获取模型回答。
import org.springframework.ai.chat.client.ChatClient;
// 引入启动回调接口；Spring Boot 启动后会调用其实现类的 run 方法。
import org.springframework.boot.CommandLineRunner;
// 引入 Profile 注解，用于决定在哪个运行配置下启用这个类。
import org.springframework.context.annotation.Profile;
// 引入组件注解，让 Spring 能扫描、创建并管理这个类的对象。
import org.springframework.stereotype.Component;

// 引入 Java 的输入读取工具，本例用它读取控制台输入。
import java.util.Scanner;

// 标记为 Spring 组件；不是执行 main，而是由 Spring 管理这个对象。
@Component
// 只有启用 console-loop-demo Profile 时才注册此组件，避免多个 Demo 同时读取控制台。
@Profile("console-loop-demo")
// public：其他类可访问；implements：实现接口，需要提供下面的 run 方法。
public class QwenChatDemo4 implements CommandLineRunner {

    // private：仅本类直接访问；final：赋值后不能换成另一个客户端引用。
    // 这是客户端对象的引用，不是聊天历史列表；复用客户端不会自动获得跨轮记忆。
    private final ChatClient chatClient;

    // 下面保留的是旧构造方法，整段已被注释，不参与编译，也不会执行。
    // 旧版只创建客户端，没有额外注册 SimpleLoggerAdvisor。
//    public QwenChatDemo4(ChatClient.Builder builder) {
//        this.chatClient = builder.build();
//    }

    // 构造方法与类同名，无返回类型；Spring 创建对象时调用它。
    // 本类只有一个有效构造方法，Spring 会自动注入已配置好的 ChatClient.Builder。
    public QwenChatDemo4(ChatClient.Builder builder) {
        // this.chatClient 指当前对象的字段；右侧使用注入的构建器配置并创建客户端。
        this.chatClient = builder
            // 给此客户端注册默认日志 Advisor；配合 properties 中的 DEBUG 设置输出日志。
            // 它记录的是框架对象；实际 HTTP JSON 由单独的 OpenAiHttpLoggingConfig 记录。
            .defaultAdvisors(new SimpleLoggerAdvisor())
            // 完成构建，得到 ChatClient；分号表示这整条赋值语句结束。
            .build();
    } // 构造方法结束。

    // 告诉编译器：下面的方法实现了接口中声明的方法，签名不匹配时会报错。
    @Override
    // public：可被外部调用；void：不返回结果；String... args：可变数量的启动参数，本例未使用。
    // 运行 SpringAiAgentDemo1Application 后，由 Spring Boot 在启动流程中调用此方法。
    public void run(String... args) {
        // 创建输入读取器；System.in 是标准输入流，在 IDEA 运行窗口中输入文字并按回车。
        Scanner scanner = new Scanner(System.in);
        // 创建本地工具对象，供每轮请求复用；此时并没有执行查询方法，也没有发送活动数据。
        ActivityTools tools = new ActivityTools();

        // 在本地控制台打印操作说明并换行；这段文字不会发送给模型。
        System.out.println("请输入问题，输入 exit 结束本次交互。");

        // 无限循环：每轮读取一个问题；遇到 break 才退出，因此能连续提问。
        while (true) {
            // print 不自动换行，使输入位置紧跟在“你：”后面；这也是本地提示。
            System.out.print("你：");

            // 判断是否还有一行可读；控制台输入时可能在此等待用户输入。
            // ! 表示取反：输入流结束、没有下一行时，进入下面的分支。
            if (!scanner.hasNextLine()) {
                // 退出最近的一层循环，也就是当前 while 循环。
                break;
            } // 输入流检查结束。

            // nextLine 读取一整行；trim 去掉首尾空白；String question 保存处理后的文字。
            String question = scanner.nextLine().trim();

            // 不区分大小写比较，所以 exit、EXIT 都能退出；检查在模型调用之前完成。
            if ("exit".equalsIgnoreCase(question)) {
                // 打印结束提示；exit 不会发送给模型，也不需要模型同意才能退出。
                System.out.println("已结束控制台交互。");
                // 只结束输入循环；不会关闭 Spring Boot 或它启动的 Tomcat 服务。
                break;
            } // exit 检查结束。

            // 判断输入是否为空或全是空白字符，避免向模型发送空问题。
            if (question.isBlank()) {
                // 告诉用户重新输入；这段提示不发送给模型。
                System.out.println("问题不能为空，请重新输入。");
                // 跳过本轮剩余代码，回到 while 开头，继续读取下一行。
                continue;
            } // 空输入检查结束。

            // 本地等待提示；下面的同步请求完成前，本线程不会进入下一轮输入。
            System.out.println("正在等待模型回答……");

            // 将可能失败的模型调用放进 try，发生异常时交给下面的 catch 处理。
            try {
                // 从现有客户端创建本轮请求；最终回答文本将赋值给 answer。
                // 本例没有添加系统提示词，也没有传入上一轮问题与回答。
                String answer = chatClient.prompt()
                        // 把控制台输入包装成 user 消息，放入本轮请求的 messages 中。
                        .user(question)
                        // 提供工具对象：框架读取 @Tool、@ToolParam 和方法签名生成工具定义。
                        // 模型收到工具名称、用途和参数说明；不会直接收到 Java 源码或模拟数据。
                        // 若模型返回工具调用请求，Spring AI 执行本地方法，再把结果交回模型。
                        .tools(tools)
                        // 选择同步调用方式；结合下面的 content 获取结果，驱动请求执行。
                        .call()
                        // 获取最终回答的文本，不把完整响应元数据赋值给 answer。
                        // 一次这样的调用可能包含多次 HTTP 请求，例如查询工具前后各一次。
                        .content();

                // 将最终回答输出到控制台；+ 表示拼接字符串，不会自动将回答保存为下轮历史。
                System.out.println("模型：" + answer);
            // 捕获上述 try 中抛出的 Exception；e 是当前异常对象，不包含 Java Error 的所有情况。
            } catch (Exception e) {
                // 使用标准错误流打印错误说明；括号内的字符串表达式在下面继续。
                System.err.println(
                        // 错误信息的固定前缀。
                        "本次请求失败，异常类型："
                                // getClass 获取异常类型；getSimpleName 获取不带包名的类名。
                                + e.getClass().getSimpleName());
                // 再打印一条排查提示；此处不重新抛出异常，循环可以继续读取问题。
                System.err.println(
                        // 提示可能原因，不代表已经确定本次故障原因。
                        "可能涉及网络、认证、模型配置或工具执行问题，请排查后重试。");
            } // 本轮异常处理结束。
        } // while 循环结束；只有退出循环后，才会到达这里。

        // 不调用 scanner.close()，因为它会连带关闭共享的 System.in 标准输入流。
    } // run 方法结束，控制权交还 Spring Boot；Web 服务可能仍在运行。
} // QwenChatDemo4 类结束。
