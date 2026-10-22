package com.example.agent.config;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo4 学习用：记录客户端已序列化的 HTTP 请求，不修改请求内容。
 */
@Configuration(proxyBeanMethods = false)
@Profile("console-loop-demo")
public class OpenAiHttpLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiHttpLoggingConfig.class);

    @Bean
    public OpenAiHttpClientBuilderCustomizer demo4HttpLoggingCustomizer() {
        AtomicInteger requestCounter = new AtomicInteger();

        return builder -> builder.interceptor(chain -> {
            Request request = chain.request();
            int requestNumber = requestCounter.incrementAndGet();
            StringBuilder output = new StringBuilder();
            output.append("\n========== 模型 HTTP 请求 #")
                    .append(requestNumber).append(" ==========\n");
            output.append(request.method()).append(' ').append(request.url()).append('\n');

            // 不输出认证信息；这里只记录拦截器位置可见的请求头。
            Headers headers = request.headers();
            for (int i = 0; i < headers.size(); i++) {
                String name = headers.name(i);
                output.append(name).append(": ")
                        .append(isSensitiveHeader(name) ? "[已隐藏]" : headers.value(i))
                        .append('\n');
            }

            RequestBody body = request.body();
            if (body != null) {
                if (body.contentType() != null) {
                    output.append("请求体类型：").append(body.contentType()).append('\n');
                }
                if (body.isOneShot() || body.isDuplex()) {
                    output.append("\n[一次性或双向请求体，不提前读取]\n");
                } else {
                    // 普通 JSON 请求体可重复写入；复制到缓冲区查看，不替换原请求体。
                    Buffer buffer = new Buffer();
                    body.writeTo(buffer);
                    output.append("\n请求体（原始 JSON）：\n")
                            .append(buffer.readUtf8()).append('\n');
                }
            }
            output.append("========== 请求 #").append(requestNumber).append(" 结束 ==========");
            log.info("{}", output);

            // 继续真正的网络请求；不读取或消耗响应体。
            try {
                Response response = chain.proceed(request);
                log.info("模型 HTTP 响应 #{}：状态码 {}", requestNumber, response.code());
                return response;
            } catch (java.io.IOException e) {
                log.info("模型 HTTP 请求 #{} 未完成：{}", requestNumber, e.getClass().getSimpleName());
                throw e;
            }
        });
    }

    private static boolean isSensitiveHeader(String name) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        return lowerName.contains("authorization")
                || lowerName.contains("key")
                || lowerName.contains("token")
                || lowerName.contains("secret")
                || lowerName.contains("cookie");
    }
}
