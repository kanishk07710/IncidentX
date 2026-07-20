package com.incidentx.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    // Bounds how many submissions can run their sandbox grading concurrently. Sized small since
    // each sandbox run is itself process/CPU heavy (see SandboxService); a queue absorbs bursts
    // instead of rejecting submissions outright.
    @Bean(name = "sandboxGradingExecutor")
    public Executor sandboxGradingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("sandbox-grading-");
        executor.initialize();
        return executor;
    }
}
