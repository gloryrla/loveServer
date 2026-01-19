package com.love;

import com.love.auth.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class LoveApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LoveApiApplication.class, args);
    }
}