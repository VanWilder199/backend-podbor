package by.marketplace.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class OtpExecutorConfig {

    private static final Logger logger = LoggerFactory.getLogger(OtpExecutorConfig.class);

    @Bean
    public ThreadPoolTaskExecutor otpTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("otp-notification-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            logger.error("OTP notification queue full! Active: {}, Queue: {}, Pool: {}", 
                exec.getActiveCount(), 
                exec.getQueue().size(),
                exec.getPoolSize());
            throw new RuntimeException("OTP notification queue full");
        });

        executor.initialize();

        return executor;
    }
}
