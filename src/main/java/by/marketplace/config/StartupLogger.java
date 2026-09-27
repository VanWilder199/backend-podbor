package by.marketplace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StartupLogger {

    private final Environment env;

    public StartupLogger(Environment env) {
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupConfig() {
        log.info("Application started: profiles={}, bepaid.testMode={}, s3.endpoint={}, app.baseUrl={}",
                String.join(",", env.getActiveProfiles()),
                env.getProperty("bepaid.test-mode"),
                env.getProperty("s3.endpoint"),
                env.getProperty("app.base-url"));
    }
}