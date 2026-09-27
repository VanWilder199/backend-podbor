package by.marketplace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Slf4j
@Configuration
public class BePaidConfig {

    @Bean
    public RestClient bePaidRestClient(BePaidProperties properties) {
        return RestClient.builder()
                .baseUrl("https://checkout.bepaid.by")
                .requestInterceptor(new BasicAuthenticationInterceptor(properties.shopId(), properties.secretKey()))
                .requestInterceptor((request, body, execution) -> {
                    long start = System.nanoTime();
                    try {
                        ClientHttpResponse response = execution.execute(request, body);
                        log.info("bePaid call: method={} path={} status={} durationMs={}",
                                request.getMethod(), request.getURI().getPath(),
                                response.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000);
                        return response;
                    } catch (IOException e) {
                        log.error("bePaid call failed: method={} path={} durationMs={}",
                                request.getMethod(), request.getURI().getPath(),
                                (System.nanoTime() - start) / 1_000_000, e);
                        throw e;
                    }
                })
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("X-API-Version", "2")
                .build();
    }
}