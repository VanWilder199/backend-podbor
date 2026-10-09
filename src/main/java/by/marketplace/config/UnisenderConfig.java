package by.marketplace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Configuration
public class UnisenderConfig {

    @Bean
    public RestClient unisenderRestClient(UnisenderProperties props) {
        return RestClient.builder()
                .baseUrl("https://api.unisender.com/" + props.region() + "/api")
                .defaultUriVariables(Map.of("apiKey", props.apiKey()))
                .requestInterceptor((request, body, execution) -> {
                    long start = System.nanoTime();

                    try {
                        ClientHttpResponse response = execution.execute(request, body);
                        log.info("unisender call: method={} path={} status={} durationMs={}",
                                request.getMethod(), request.getURI().getPath(),
                                response.getStatusCode().value(),(System.nanoTime() - start) / 1_000_000);
                        return  response;
                    } catch (IOException e) {
                        log.error("unisender call failed: method={} path={}",
                                request.getMethod(), request.getURI().getPath(), e);
                        throw e;
                    }
                })
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
