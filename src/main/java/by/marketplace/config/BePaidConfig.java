package by.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
public class BePaidConfig {

    @Bean
    public RestClient bePaidRestClient(BePaidProperties properties) {
        return RestClient.builder()
                .baseUrl("https://checkout.bepaid.by")
                .requestInterceptor(new BasicAuthenticationInterceptor(properties.shopId(), properties.secretKey()))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-API-Version", "2")
                .build();
    }
}
