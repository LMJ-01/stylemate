package com.stylemate.service;

import com.stylemate.dto.AvatarUpdateDto;
import com.stylemate.model.Avatar;
import com.stylemate.model.User;
import com.stylemate.repository.AvatarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private final AvatarRepository avatarRepository;

    /** 아바타 조회 (없으면 기본값 객체만 반환, DB에는 저장 안 함) */
    @Transactional(readOnly = true)
    public Avatar getOrDefault(User user) {
        return avatarRepository.findByUser(user)
                .orElse(Avatar.builder().user(user).build());
    }

    /** 아바타 조회 (없으면 새로 생성 및 저장) */
    @Transactional
    public Avatar getOrCreate(User user) {
        return avatarRepository.findByUser(user)
                .orElseGet(() -> avatarRepository.save(Avatar.builder().user(user).build()));
    }

    /** 아바타 업데이트 및 저장 */
    @Transactional
    public Avatar save(User user, AvatarUpdateDto dto) {
        Avatar av = avatarRepository.findByUser(user)
                .orElseGet(() -> Avatar.builder().user(user).build());

        // 안전한 값 범위 클램프
        if (dto.getHeightCm() != null)      av.setHeightCm(Math.max(120, Math.min(220, dto.getHeightCm())));
        if (dto.getWeightKg() != null)      av.setWeightKg(Math.max(40,  Math.min(160, dto.getWeightKg())));
        if (dto.getBodyShape() != null)     av.setBodyShape(dto.getBodyShape());
        if (dto.getShoulderScale() != null) av.setShoulderScale(clamp(dto.getShoulderScale(), 0.9, 1.3));
        if (dto.getHeadScale() != null)     av.setHeadScale(clamp(dto.getHeadScale(), 0.85, 1.2));

        // 🔥 여기 수정
        if (dto.getSkinTone() != null && !dto.getSkinTone().isBlank()) {
            av.setSkinTone(dto.getSkinTone());
        }

        if (dto.getToneBrightness() != null)
            av.setToneBrightness(clamp(dto.getToneBrightness(), 0.85, 1.15));

        if (dto.getGender() != null)        av.setGender(dto.getGender());
        if (dto.getPose() != null)          av.setPose(dto.getPose());

        return avatarRepository.save(av);
    }

    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ===========================================================
    // ✅ 사용자 맞춤 마네킹 SVG 생성 (피부색·체형·키 등 반영)
    // ===========================================================
    public String buildMannequinDataUrl(Avatar av) {
        // 1) HEX 그대로 사용하되, null/빈 값이면 기본값
        String fill = (av.getSkinTone() != null && !av.getSkinTone().isBlank())
                ? av.getSkinTone()
                : "#cfa18a";

        // 2) 체형에 따라 폭 스케일
        double widthScale = 1.0;
        if ("slim".equalsIgnoreCase(av.getBodyShape())) widthScale = 0.9;
        if ("plus".equalsIgnoreCase(av.getBodyShape())) widthScale = 1.12;

        // 3) 키에 따라 높이 스케일
        double heightScale = Math.max(0.8, Math.min(1.2,
                (av.getHeightCm() != null ? av.getHeightCm() : 170) / 170.0));

        String svg =
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 320'>"
          + "  <g transform='translate(100,160) scale(" + widthScale + "," + heightScale + ") translate(-100,-160)'>"
          + "    <circle cx='100' cy='50' r='32' fill='" + fill + "'/>"
          + "    <rect x='86' y='82' width='28' height='60' rx='14' fill='" + fill + "'/>"
          + "    <rect x='60' y='100' width='80' height='90' rx='30' fill='" + fill + "'/>"
          + "    <rect x='70' y='190' width='20' height='90' rx='10' fill='" + fill + "'/>"
          + "    <rect x='110' y='190' width='20' height='90' rx='10' fill='" + fill + "'/>"
          + "    <rect x='40' y='110' width='20' height='70' rx='10' fill='" + fill + "'/>"
          + "    <rect x='140' y='110' width='20' height='70' rx='10' fill='" + fill + "'/>"
          + "  </g>"
          + "</svg>";

        String encoded = URLEncoder.encode(svg, StandardCharsets.UTF_8);
        return "data:image/svg+xml;utf8," + encoded;
    }


}
