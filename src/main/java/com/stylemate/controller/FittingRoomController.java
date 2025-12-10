package com.stylemate.controller;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.model.Clothes;
import com.stylemate.model.Clothes.Category;
import com.stylemate.model.FittingRoomSet;
import com.stylemate.model.User;
import com.stylemate.service.AvatarService;
import com.stylemate.service.ClothesService;
import com.stylemate.service.FittingRoomSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user/profile")
public class FittingRoomController {

    private final ClothesService clothesService;
    private final FittingRoomSetService fittingRoomSetService;
    private final AvatarService avatarService;

    /** 로그인 사용자 검사 + 본인 페이지 권한 검사 */
    private boolean isOwner(Long pathId, UserDetailsImpl userDetails) {
        return userDetails != null
                && userDetails.getUser() != null
                && Objects.equals(userDetails.getUser().getId(), pathId);
    }

    /** ✅ 피팅룸 페이지 (본인만 접근) */
    @GetMapping("/{id}/fittingroom")
    public String fittingRoomPage(@PathVariable Long id,
                                  @AuthenticationPrincipal UserDetailsImpl userDetails,
                                  Model model) {
        if (!isOwner(id, userDetails)) {
            return "error/403";
        }

        User me = userDetails.getUser();

        // 아바타 주입 (피팅룸에서 항상 동일한 아바타 사용)
        var avatar = avatarService.getOrCreate(me);

        model.addAttribute("user", me);
        model.addAttribute("me", me);      // 템플릿에서 user / me 둘 다 사용 가능
        model.addAttribute("avatar", avatar);

        // 내부 DB 아이템 목록
        model.addAttribute("clothesList", clothesService.getAll());

        // templates/user/fittingroom.html
        return "user/fittingroom";
    }

    /** ✅ 내 피팅룸 저장 목록 (본인만 접근) */
    @GetMapping("/{id}/fittingroom/saved")
    public String savedPage(@PathVariable Long id,
                            @AuthenticationPrincipal UserDetailsImpl userDetails,
                            Model model) {
        if (!isOwner(id, userDetails)) {
            return "error/403";
        }

        User me = userDetails.getUser();
        model.addAttribute("user", me);
        model.addAttribute("me", me);
        model.addAttribute("sets", fittingRoomSetService.getUserSets(me));

        // templates/user/fittingroom_saved.html
        return "user/fittingroom_saved";
    }

    /** ✅ 랜덤 추천 (본인만) */
    @GetMapping("/{id}/fittingroom/random")
    @ResponseBody
    public ResponseEntity<Clothes> randomOne(@PathVariable Long id,
                                             @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (!isOwner(id, userDetails)) {
            return ResponseEntity.status(403).build();
        }

        Clothes pick = clothesService.getRandomOne();
        if (pick == null) {
            return ResponseEntity.ok().build(); // 아이템 없을 때 빈 응답
        }
        return ResponseEntity.ok(pick);
    }

    /** ✅ 복합 필터 (본인만) – 피팅룸 전용 느슨한 필터 */
    @GetMapping("/{id}/fittingroom/filter/advanced")
    @ResponseBody
    public ResponseEntity<List<Clothes>> filterAdvanced(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subCategory,   // ⭐ 세부 카테고리 추가
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Integer maxPrice
    ) {
        if (!isOwner(id, userDetails)) {
            return ResponseEntity.status(403).build();
        }

        Category catEnum = parseCategory(category);

        String subCatNorm = normalize(subCategory);  // ⭐ 세부 카테고리 정규화
        String colorNorm  = normalize(color);
        String brandNorm  = normalize(brand);
        String genderNorm = normalize(gender);

        List<Clothes> all = clothesService.getAll();

        List<Clothes> filtered = all.stream()
                // 1) 상위 카테고리 (지정 안 했으면 통과)
                .filter(c -> catEnum == null || c.getCategory() == catEnum)

                // 2) 세부 카테고리 (반팔/긴팔/후드티/바람막이 등) - 정확히 일치
                .filter(c -> {
                    if (subCatNorm == null) return true;
                    String sc = normalize(c.getSubCategory());
                    return sc != null && sc.equals(subCatNorm);
                })

                // 3) 색상 (부분 포함, 대소문자 무시)
                .filter(c -> {
                    if (colorNorm == null) return true;
                    String cColor = normalize(c.getColor());
                    return cColor != null && cColor.contains(colorNorm);
                })

                // 4) 브랜드 (부분 포함, 대소문자 무시)
                .filter(c -> {
                    if (brandNorm == null) return true;
                    String cBrand = normalize(c.getBrand());
                    return cBrand != null && cBrand.contains(brandNorm);
                })

                // 5) 성별 (선택 안 했으면 통과)
                .filter(c -> {
                    if (genderNorm == null) return true;
                    String g = normalize(c.getGender());
                    return g != null && g.equals(genderNorm);
                })

                // 6) 최대 가격
                .filter(c -> {
                    if (maxPrice == null || maxPrice <= 0) return true;
                    Integer price = c.getPrice();
                    return price == null || price <= maxPrice;
                })

                .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    /** ✅ 피팅 조합 저장 (본인만) */
    @PostMapping("/{id}/fittingroom/save")
    @ResponseBody
    public ResponseEntity<String> saveSet(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetailsImpl userDetails,
                                          @RequestBody(required = false) FittingRoomSet payload) {
        if (!isOwner(id, userDetails)) {
            return ResponseEntity.status(403).body("본인만 저장 가능합니다.");
        }
        if (payload == null) {
            return ResponseEntity.badRequest().body("요청 본문이 비었습니다.");
        }

        User me = userDetails.getUser();

        FittingRoomSet set = new FittingRoomSet();
        set.setUser(me);
        set.setName((payload.getName() == null || payload.getName().isBlank())
                ? "My Outfit " + System.currentTimeMillis()
                : payload.getName());
        set.setTopImage(payload.getTopImage());
        set.setBottomImage(payload.getBottomImage());
        set.setOuterImage(payload.getOuterImage());
        set.setShoesImage(payload.getShoesImage());
        set.setAccessoryImage(payload.getAccessoryImage());
        set.setFaceImage(payload.getFaceImage());

        FittingRoomSet saved = fittingRoomSetService.save(set);

        return ResponseEntity
                .created(URI.create("/user/profile/" + id + "/fittingroom/saved"))
                .body("저장 완료! id=" + saved.getId());
    }

    /** ✅ 저장된 세트 단일 조회 (미리보기/공유용) */
    @GetMapping("/{id}/fittingroom/api/{setId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSet(@PathVariable Long id,
                                                      @PathVariable Long setId,
                                                      @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (!isOwner(id, userDetails)) {
            return ResponseEntity.status(403).build();
        }
        FittingRoomSet found = fittingRoomSetService.getById(setId);
        if (found == null || !found.getUser().getId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", found.getId());
        body.put("name", found.getName());
        body.put("createdAt", found.getCreatedAt());
        body.put("topImage", found.getTopImage());
        body.put("bottomImage", found.getBottomImage());
        body.put("outerImage", found.getOuterImage());
        body.put("shoesImage", found.getShoesImage());
        body.put("accessoryImage", found.getAccessoryImage());
        body.put("faceImage", found.getFaceImage());
        return ResponseEntity.ok(body);
    }

    /** ✅ 세트 삭제 (본인만) */
    @DeleteMapping("/{id}/fittingroom/delete/{setId}")
    @ResponseBody
    public ResponseEntity<String> deleteSet(@PathVariable Long id,
                                            @PathVariable Long setId,
                                            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (!isOwner(id, userDetails)) {
            return ResponseEntity.status(403).body("권한 없음");
        }

        boolean ok = fittingRoomSetService.deleteById(setId, userDetails.getUser());
        return ok
                ? ResponseEntity.ok("삭제 완료")
                : ResponseEntity.badRequest().body("삭제 실패");
    }

    /** 내부 유틸: 카테고리 파싱 */
    private Category parseCategory(String raw) {
        if (raw == null) return null;
        switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "top":       return Category.top;
            case "bottom":    return Category.bottom;
            case "outer":     return Category.outer;
            case "shoes":     return Category.shoes;
            case "accessory": return Category.accessory;
            default:          return null;
        }
    }

    /** 내부 유틸: 문자열 정규화 (null/공백 → null, 소문자) */
    private String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toLowerCase(Locale.ROOT);
    }
}
