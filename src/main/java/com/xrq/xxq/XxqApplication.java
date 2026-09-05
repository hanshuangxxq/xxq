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
        // spring.aot.processing=true 表示当前是 process-aot 构建期处理（SpringApplicationAotProcessor 会反射调用本方法），
        // 此时必须保持常规模式做全量扫描：若启用 AOT 会加载 target/classes 里的旧产物（含 instance supplier 形式的
        // internalImportAwareAotProcessor 注册），导致新一轮生成直接失败
        if (System.getProperty("spring.aot.enabled") == null
                && !"true".equals(System.getProperty("spring.aot.processing"))) {
            try {
                Class.forName("com.xrq.xxq.XxqApplication__ApplicationContextInitializer");
                System.setProperty("spring.aot.enabled", "true");
            } catch (ClassNotFoundException ignored) {
            }
        }
        SpringApplication.run(XxqApplication.class, args);
    }
}
