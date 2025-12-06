package com.stylemate.controller;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.dto.AvatarUpdateDto;
import com.stylemate.model.Avatar;
import com.stylemate.model.User;
import com.stylemate.service.AvatarService;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user/profile")
public class AvatarController {

    private final AvatarService avatarService;

    /** 본인 페이지인지 검사 */
    private boolean isOwner(Long pathId, UserDetailsImpl userDetails) {
        return userDetails != null
                && userDetails.getUser() != null
                && Objects.equals(userDetails.getUser().getId(), pathId);
    }

    /** 아바타 편집 페이지 */
    @GetMapping("/{id}/avatar/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetailsImpl userDetails,
                       Model model) {
        if (!isOwner(id, userDetails)) return "error/403";
        User me = userDetails.getUser();
        // 서비스는 기본값이 없거나 없으면 생성하는 형태로 통일
        Avatar avatar = avatarService.getOrCreate(me);
        model.addAttribute("user", me);
        model.addAttribute("avatar", avatar);
        return "user/avatar_edit";
    }

    /** 아바타 저장 (폼 POST) */
    @PostMapping("/{id}/avatar/save")
    public String saveAvatar(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetailsImpl userDetails,
                             @ModelAttribute AvatarUpdateDto dto) {
        if (!isOwner(id, userDetails)) return "error/403";
        avatarService.save(userDetails.getUser(), dto);
        return "redirect:/user/profile/" + id + "/fittingroom";
    }

    /** 피팅룸이 불러가는 JSON (개인화 값) */
    @GetMapping(value = "/{id}/avatar.json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> avatarJson(@PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (!isOwner(id, userDetails)) return ResponseEntity.status(403).build();

        Avatar av = avatarService.getOrCreate(userDetails.getUser());

        Map<String, Object> payload = new HashMap<>();
        payload.put("heightCm",        av.getHeightCm()       != null ? av.getHeightCm()       : 175);
        payload.put("weightKg",        av.getWeightKg()       != null ? av.getWeightKg()       : 70);
        payload.put("bodyShape",       av.getBodyShape()      != null ? av.getBodyShape()      : "regular");
        payload.put("shoulderScale",   av.getShoulderScale()  != null ? av.getShoulderScale()  : 1.0);
        payload.put("headScale",       av.getHeadScale()      != null ? av.getHeadScale()      : 1.0);
        payload.put("skinToneHex",     av.getSkinTone()       != null ? av.getSkinTone()       : "#e6cbb3");
        payload.put("toneBrightness",  av.getToneBrightness() != null ? av.getToneBrightness() : 1.0);
        payload.put("gender",          av.getGender()         != null ? av.getGender()         : "unisex");
        payload.put("pose",            av.getPose()           != null ? av.getPose()           : "neutral");

        double heightScale = clamp(map(toD(payload.get("heightCm")), 150, 190, 0.9, 1.1), 0.85, 1.15);
        double weightScale = clamp(map(toD(payload.get("weightKg")), 50, 95, 0.92, 1.18), 0.85, 1.30);
        payload.put("heightScale", heightScale);
        payload.put("weightScale", weightScale);

        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        return ResponseEntity.ok().headers(headers).body(payload);
    }


    // --------- 내부 계산 유틸 ---------
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
    private static double map(double x, double inMin, double inMax, double outMin, double outMax) {
        double t = (x - inMin) / (inMax - inMin);
        t = clamp(t, 0.0, 1.0);
        return outMin + t * (outMax - outMin);
    }
    private static double toD(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); }
        catch (Exception e) { return 0d; }
    }
}
