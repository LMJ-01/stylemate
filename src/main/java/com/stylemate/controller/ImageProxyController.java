package com.stylemate.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("/img")
public class ImageProxyController {

	private final RestTemplate restTemplate; 

    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxy(@RequestParam("url") String url) {
        try {
            // 간단 방어 로직 (원하면 도메인 화이트리스트 추가)
            if (url == null || url.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            ResponseEntity<byte[]> res = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    byte[].class
            );

            HttpHeaders headers = new HttpHeaders();
            MediaType ct = res.getHeaders().getContentType();
            headers.setContentType(ct != null ? ct : MediaType.IMAGE_JPEG);
            headers.setCacheControl(CacheControl.noStore());

            return new ResponseEntity<>(res.getBody(), headers, res.getStatusCode());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
