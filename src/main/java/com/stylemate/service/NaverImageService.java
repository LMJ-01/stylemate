package com.stylemate.service;

import com.stylemate.dto.NaverImageSearchDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverImageService {

    @Value("${naver.search.clientId}")
    private String clientId;

    @Value("${naver.search.clientSecret}")
    private String clientSecret;

    @Value("${naver.search.base:https://openapi.naver.com}")
    private String baseUrl;

    private RestTemplate restTemplate;

    private final RestTemplateBuilder restTemplateBuilder;

    @PostConstruct
    void init() {
        // 공통 헤더 인터셉터 (모든 요청에 자동 첨부)
        ClientHttpRequestInterceptor authHeader = (req, body, ex) -> {
            req.getHeaders().set("X-Naver-Client-Id", clientId);
            req.getHeaders().set("X-Naver-Client-Secret", clientSecret);
            req.getHeaders().setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            return ex.execute(req, body);
        };

        this.restTemplate = restTemplateBuilder
                .additionalInterceptors(authHeader)
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 네이버 이미지 검색 (공식 파라미터: query, display, start, sort, filter)
     *
     * @param query   검색어(필수)
     * @param display 1~100 (기본 10)
     * @param start   1~1000 (기본 1)
     * @param sort    sim(정확도) | date(최신) (기본 sim)
     * @param filter  all|large|medium|small (선택)
     */
    public NaverImageSearchDto search(String query,
                                      Integer display,
                                      Integer start,
                                      String sort,
                                      String filter) {

        if (query == null || query.isBlank()) {
            log.warn("[NaverImageService] query is blank");
            return emptyResult();
        }

        int d = clamp(display, 10, 100, 10);
        int s = clamp(start, 1, 1000, 1);
        String sortSafe = ("date".equalsIgnoreCase(sort)) ? "date" : "sim";
        String filterSafe = (filter == null || filter.isBlank()) ? null : filter.toLowerCase();

        // ✅ UriComponentsBuilder에 한글 그대로 넣고, 마지막에 encode()로 인코딩
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/v1/search/image")
                .queryParam("query", query)   // "와이드 팬츠" 그대로 넣어도 됨
                .queryParam("display", d)
                .queryParam("start", s)
                .queryParam("sort", sortSafe);

        if (filterSafe != null) {
            builder.queryParam("filter", filterSafe);
        }

        // ❌ .build(true) 사용 금지 (이미 인코딩된 값으로 간주해서 예외 발생)
        // ✅ encode()로 UTF-8 기준 안전하게 인코딩
        URI uri = builder
                .encode()   // 쿼리 파라미터 한글 → %EC... 형태로 인코딩
                .build()
                .toUri();

        try {
            ResponseEntity<NaverImageSearchDto> resp = restTemplate.exchange(
                    uri, HttpMethod.GET, HttpEntity.EMPTY,
                    new ParameterizedTypeReference<NaverImageSearchDto>(){}
            );

            if (!resp.getStatusCode().is2xxSuccessful()) {
                log.warn("[NaverImageService] non-2xx status: {} for {}", resp.getStatusCode(), uri);
                return emptyResult();
            }

            NaverImageSearchDto body = resp.getBody();
            return (body != null) ? body : emptyResult();

        } catch (HttpStatusCodeException e) {
            log.error("[NaverImageService] API error {} {}: {}",
                    e.getStatusCode().value(), e.getStatusCode().getReasonPhrase(), e.getResponseBodyAsString(), e);
            return emptyResult();
        } catch (Exception e) {
            log.error("[NaverImageService] request failed: {}", uri, e);
            return emptyResult();
        }
    }

    /**
     * 가장 간단한 오버로드: 정확도 정렬, 20개
     */
    public NaverImageSearchDto search(String query, int display) {
        return search(query, display, 1, "sim", null);
    }

    /**
     * 이미지 URL만 뽑아서 리턴(https만, 빈 값 필터링).
     * proxy=true이면 /img/proxy?url=... 형태로 변환.
     */
    public List<String> searchImageUrls(String query, int display, boolean proxy) {
        NaverImageSearchDto dto = search(query, display);
        if (dto == null || dto.getItems() == null) return Collections.emptyList();

        List<String> urls = new ArrayList<>();
        dto.getItems().forEach(it -> {
            // Naver 응답에서 대표 이미지 필드(썸네일/링크 등)는 DTO 설계에 맞춰 선택
            String raw = it.getLink(); // 예: getLink()
            if (raw != null && raw.startsWith("http")) {
                urls.add(proxy ? toProxyUrl(raw) : raw);
            }
        });
        return urls;
    }

    /** 프록시 URL로 변환 (ImageProxyController와 연동) */
    public String toProxyUrl(String rawUrl) {
        // rawUrl은 이미 http/https 완성된 URL이므로 build(true) 사용해도 OK
        return UriComponentsBuilder.fromPath("/img/proxy")
                .queryParam("url", rawUrl)
                .build(true)
                .toUriString();
    }

    private static int clamp(Integer v, int min, int max, int def) {
        if (v == null) return def;
        return Math.max(min, Math.min(max, v));
    }

    private static NaverImageSearchDto emptyResult() {
        NaverImageSearchDto dto = new NaverImageSearchDto();
        dto.setTotal(0);
        dto.setItems(Collections.emptyList());
        return dto;
    }
}
