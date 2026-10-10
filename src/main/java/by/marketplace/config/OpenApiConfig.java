package by.marketplace.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI martketPlaceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MarketPlace API")
                        .version("1.0")
                        .description("Auto-reports marketplace: buyers, inspectors, admins")
                ).components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addSecuritySchemes("TelegramData", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Telegram-Data")
                        )
                );

    }

    @Bean
    public GroupedOpenApi inspectorApi() {
        return GroupedOpenApi.builder()
                .group("inspector")
                .pathsToMatch("/inspector/**")
                .addOpenApiCustomizer(api -> api.setSecurity(List.of(
                        new SecurityRequirement().addList("TelegramData")
                )))
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch("/admin/**")
                .addOpenApiCustomizer(api -> api.setSecurity(List.of(
                        new SecurityRequirement().addList("Bearer"))))
                .build();
    }

    @Bean
    public GroupedOpenApi buyerApi() {
        return GroupedOpenApi.builder()
                .group("buyer")
                .pathsToMatch("/auth/**", "/cars/**", "/purchases/**", "/reports/**")
                .addOpenApiCustomizer(api -> api.setSecurity(List.of(
                        new SecurityRequirement().addList("Bearer"))))
                .build();
    }

}
