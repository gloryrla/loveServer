package com.love.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${app.ai-server.url:http://localhost:5001}")
    private String aiServerUrl;

    @Bean
    public WebClient aiServerWebClient() {
        HttpClient client = HttpClient.create().responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder()
                .baseUrl(aiServerUrl)
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }
}
