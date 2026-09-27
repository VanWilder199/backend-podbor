package by.marketplace.config;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Slf4j
@Configuration
public class OtpExecutorConfig {

    @Bean
    public ThreadPoolTaskExecutor otpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("otp-notification-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("OTP notification queue full! Active: {}, Queue: {}, Pool: {}",
                exec.getActiveCount(),
                exec.getQueue().size(),
                exec.getPoolSize());
            throw new RuntimeException("OTP notification queue full");
        });

        // Пробрасываем MDC (traceId, userId) из потока запроса в фоновый поток OTP.
        executor.setTaskDecorator(runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) MDC.setContextMap(context);
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        });

        executor.initialize();

        return executor;
    }
}
