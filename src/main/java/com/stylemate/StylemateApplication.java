package com.stylemate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class StylemateApplication {

    public static void main(String[] args) {
        SpringApplication.run(StylemateApplication.class, args);
    }

    // 🔹 ImageProxyController 에 주입할 RestTemplate 빈 등록
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
