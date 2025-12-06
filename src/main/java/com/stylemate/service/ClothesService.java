package com.stylemate.service;

import com.stylemate.model.Clothes;
import com.stylemate.model.Clothes.Category;
import com.stylemate.repository.ClothesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClothesService {

    private final ClothesRepository clothesRepository;
    private final Random random = new Random();

    /** 전체 조회 */
    public List<Clothes> getAll() {
        return clothesRepository.findAll();
    }

    /** 랜덤 1개 */
    public Clothes getRandomOne() {
        List<Clothes> all = clothesRepository.findAll();
        if (all.isEmpty()) return null;
        return all.get(random.nextInt(all.size()));
    }

    /** 단일 필터들 (UI에서 개별 요청용) */
    public List<Clothes> byCategory(Category category) {
        return clothesRepository.findByCategory(category);
    }

    public List<Clothes> byColor(String color) {
        return clothesRepository.findByColorIgnoreCase(safe(color));
    }

    public List<Clothes> byBrand(String brand) {
        return clothesRepository.findByBrandIgnoreCase(safe(brand));
    }

    public List<Clothes> byMaxPrice(int maxPrice) {
        return clothesRepository.findByPriceLessThanEqual(maxPrice);
    }

    /**
     * 🔥 복합 필터 (category / subCategory / color / brand / gender / maxPrice)
     * 데이터가 많지 않다는 전제에서 메모리 필터링으로 처리
     */
    public List<Clothes> filter(
            Category category,
            String subCategory,   // ⭐ 세부 카테고리
            String color,
            String brand,
            String gender,
            Integer maxPrice
    ) {
        String subNorm    = safe(subCategory);
        String colorNorm  = safe(color);
        String brandNorm  = safe(brand);
        String genderNorm = safe(gender);

        return clothesRepository.findAll().stream()
                // 1) 카테고리: enum 직접 비교
                .filter(c -> category == null || c.getCategory() == category)

                // 2) 세부 카테고리
                //    - 지금은 DB에 sub_category가 거의 NULL이라
                //      사용자가 세부 카테고리를 골라도 DB 옷은 일단 다 보여주고,
                //      나중에 sub_category 값을 채우면 그때부터 진짜 필터가 걸리도록 함.
                .filter(c -> {
                    if (subNorm == null) return true;   // 세부 카테고리 선택 안 했으면 전체 통과
                    String dbSub = safe(c.getSubCategory());
                    if (dbSub == null) {
                        // 🔥 아직 세부 카테고리가 안 채워진 옷은 "일단 포함"
                        return true;
                    }
                    // 나중에 DB에 'short_sleeve', 'windbreaker' 같은 값이 들어가면
                    // 여기서 부분 일치로 필터 됨
                    return containsIgnoreCase(dbSub, subNorm);
                })

                // 3) 색상: 부분 일치, 대소문자 무시
                .filter(c -> colorNorm == null || containsIgnoreCase(c.getColor(), colorNorm))

                // 4) 브랜드: 부분 일치, 대소문자 무시
                .filter(c -> brandNorm == null || containsIgnoreCase(c.getBrand(), brandNorm))

                // 5) 성별: 영어/한글 둘 다 어느 정도 매핑
                .filter(c -> {
                    if (genderNorm == null) return true;
                    String g = safe(c.getGender());
                    if (g == null) return false;

                    // 그대로 일치 (male, female, unisex 등)
                    if (equalsIgnoreCase(g, genderNorm)) return true;

                    // 영어 선택값을 한글/축약형으로 매핑
                    String gn = genderNorm.toLowerCase(Locale.ROOT);
                    String gv = g.toLowerCase(Locale.ROOT);

                    if (gn.equals("male")) {
                        return gv.contains("남") || gv.equals("m") || gv.contains("man");
                    }
                    if (gn.equals("female")) {
                        return gv.contains("여") || gv.equals("f") || gv.contains("woman");
                    }
                    if (gn.equals("unisex")) {
                        return gv.contains("공용") || gv.contains("uni");
                    }
                    return false;
                })

                // 6) 가격: null 체크 후 비교
                .filter(c -> maxPrice == null || (c.getPrice() != null && c.getPrice() <= maxPrice))

                .collect(Collectors.toList());
    }

    /** 공백 제거 + 빈 문자열 → null */
    private static String safe(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    /** 부분 일치 + 대소문자 무시 */
    private static boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        String k = keyword.toLowerCase(Locale.ROOT);
        return t.contains(k);
    }
}
