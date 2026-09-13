package org.example.lifecomposer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dedicated executor for SSE streaming so the request thread returns immediately. */
@Configuration
public class AsyncConfig {

    @Bean(name = "chatStreamExecutor", destroyMethod = "shutdown")
    public ExecutorService chatStreamExecutor() {
        return Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "chat-stream");
            thread.setDaemon(true);
            return thread;
        });
    }
}
