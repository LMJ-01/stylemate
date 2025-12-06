package com.stylemate.controller;

import com.stylemate.dto.NaverImageSearchDto;
import com.stylemate.service.NaverImageService;
import com.stylemate.service.ClothesCropService;   // 🔹 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/images")
public class ImageSearchController {

    private final NaverImageService naverImageService;
    private final ClothesCropService clothesCropService;   // 🔹 추가

    /** ✅ 간단 헬스체크 */
    @GetMapping("/ping")
    public String ping() {
        return "ok";
    }

    /** ✅ 네이버 이미지 검색 (외부 API 호출 + 크롭 이미지 포함) */
    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "display", defaultValue = "12") int display
    ) {
        // 🔹 우선순위: q > query
        String keyword = (q != null && !q.trim().isEmpty())
                ? q.trim()
                : (query != null ? query.trim() : null);

        if (keyword == null || keyword.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "query 또는 q 파라미터가 필요합니다.");
        }

        display = Math.max(1, Math.min(display, 30));
        NaverImageSearchDto dto = naverImageService.search(keyword, display);

        if (dto == null || dto.getItems() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "네이버 이미지 검색 실패");
        }

        // 🔥 여기서 크롭된 이미지까지 넣어줌
        return dto.getItems().stream().map(it -> {
            String imageUrl = it.getLink();

            // 🔹 파이썬 서버에서 이미지 PNG(base64) 받기
            String cropped = clothesCropService.getCroppedImageDataUrl(imageUrl);

            Map<String, Object> map = new HashMap<>();
            map.put("title", stripHtml(it.getTitle()));
            map.put("imageUrl", imageUrl);                 // 원본 URL
            map.put("thumbUrl", it.getThumbnail());
            map.put("width", safeInt(it.getSizewidth()));
            map.put("height", safeInt(it.getSizeheight()));
            map.put("croppedImage", cropped);              // 🔹 추가된 필드
            return map;
        }).collect(Collectors.toList());
    }

    /** ✅ HTML 태그 제거 */
    private String stripHtml(String s) {
        return s == null ? "" : s.replaceAll("<[^>]+>", "");
    }

    /** ✅ 안전한 숫자 변환 */
    private Integer safeInt(String s) {
        try {
            return (s == null || s.isBlank()) ? null : Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
