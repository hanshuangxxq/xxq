package com.xrq.xxq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 应用入口。
 *
 * @类名 XxqApplication
 * @Date 2026/6/5
 */
@SpringBootApplication
public class XxqApplication {

    public static void main(String[] args) {
        // 打包后 jar 含 AOT 生成的 __ApplicationContextInitializer 类时启用 AOT；-Dspring.aot.enabled=false 可禁用
        if (System.getProperty("spring.aot.enabled") == null) {
            try {
                Class.forName("com.xrq.xxq.XxqApplication__ApplicationContextInitializer");
                System.setProperty("spring.aot.enabled", "true");
            } catch (ClassNotFoundException ignored) {
            }
        }
        SpringApplication.run(XxqApplication.class, args);
    }
}
