package com.stylemate.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ClothesCropService {

    private final RestTemplate restTemplate;
    private static final String CROP_SERVER_URL = "http://127.0.0.1:5001/crop-clothes";

    public ClothesCropService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 이미지 URL을 파이썬 크롭 서버에 보내서
     * data:image/png;base64,... 형태의 문자열을 돌려받는다.
     * 실패하면 null 리턴.
     */
    public String getCroppedImageDataUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("[ClothesCropService] imageUrl 이 비어있어서 크롭을 건너뜁니다.");
            return null;
        }

        try {
            Map<String, String> body = new HashMap<>();
            body.put("imageUrl", imageUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    CROP_SERVER_URL,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("[ClothesCropService] 크롭 서버 응답 코드: {}", response.getStatusCode());
                return null;
            }

            Map<String, Object> respBody = response.getBody();
            if (respBody == null) {
                log.warn("[ClothesCropService] 크롭 서버 응답 body 가 null 입니다.");
                return null;
            }

            Object value = respBody.get("croppedImage");
            if (value instanceof String cropped && !cropped.isBlank()) {
                // ✅ data:image/png;base64,... 형태
                return cropped;
            } else {
                log.warn("[ClothesCropService] croppedImage 필드가 없거나 문자열이 아닙니다. respBody={}", respBody);
                return null;
            }
        } catch (Exception e) {
            log.error("[ClothesCropService] 크롭 서버 호출 중 예외 발생. url={}", imageUrl, e);
            return null;
        }
    }
}
