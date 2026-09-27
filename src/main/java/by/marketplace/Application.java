package by.marketplace;

import by.marketplace.config.AppProperties;
import by.marketplace.config.BePaidProperties;
import by.marketplace.config.S3Properties;
import by.marketplace.config.TelegramProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({
        TelegramProperties.class,
        S3Properties.class,
        BePaidProperties.class,
        AppProperties.class
})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
