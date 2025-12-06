package com.stylemate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // ✅ 업로드 폴더 절대경로 지정
        Path uploadDir = Paths.get(System.getProperty("user.home"), "stylemate-uploads");
        String uploadPath = uploadDir.toUri().toString(); // → file:///C:/Users/명준/stylemate-uploads/

        // ✅ 피드/프로필 이미지 정적 서빙
        //   예: http://localhost:8083/uploads/abc.jpg → C:/Users/.../stylemate-uploads/abc.jpg
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath)
                .setCachePeriod(3600); // 캐시 1시간

        // ✅ 정적 리소스 매핑 (JS, CSS, 이미지 등)
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/images/**", "/img/**")
                .addResourceLocations("classpath:/static/images/", "classpath:/static/img/");

        // ✅ favicon 및 기타 루트 정적 파일
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/favicon.ico");

        // ✅ SPA나 fallback 처리 필요 시 추가 (현재 Thymeleaf라면 생략 가능)
        // registry.addResourceHandler("/**")
        //         .addResourceLocations("classpath:/static/");
    }
}
