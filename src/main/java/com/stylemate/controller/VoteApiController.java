package com.stylemate.controller;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.dto.VoteSummaryDto;
import com.stylemate.model.VoteOption;
import com.stylemate.model.VoteState;
import com.stylemate.service.VoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/votes")
@RequiredArgsConstructor
public class VoteApiController {

    private final VoteService voteService;

    /**
     * ✔ 투표 요약 조회 (JS 초기 로딩·주기적 갱신용)
     * GET /api/votes/{feedId}
     */
    @GetMapping("/{feedId}")
    public ResponseEntity<?> getSummary(
            @PathVariable Long feedId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        try {
            Long viewerId = (user != null && user.getUser() != null)
                    ? user.getUser().getId()
                    : null;

            VoteSummaryDto s = voteService.getSummary(feedId, viewerId);
            VoteState state = voteService.getState(feedId);

            // 상태 텍스트 변환
            String statusText = switch (state) {
                case SCHEDULED -> "투표 예정";
                case ACTIVE    -> "투표 진행 중";
                case CLOSED    -> "투표 마감";
                default        -> "알 수 없음";
            };

            LocalDateTime start = s.getStartAt();
            LocalDateTime end   = s.getEndAt();

            // ⚠ Map.of는 null을 허용하지 않음 → 모든 값은 null 여부 확인해야 함
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("countA", s.getCountA());
            body.put("countB", s.getCountB());
            body.put("total", s.getTotal());
            body.put("ratioA", s.getRatioA());
            body.put("ratioB", s.getRatioB());
            body.put("visible", s.isVisible());
            body.put("myChoice", s.getMyChoice());
            body.put("status", statusText);
            body.put("startAt", start);
            body.put("endAt", end);

            return ResponseEntity.ok(body);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            // 서버 내부 오류
            return ResponseEntity.status(500)
                    .body(Map.of("error", "vote summary error"));
        }
    }

    /**
     * ✔ 투표하기
     * POST /api/votes/{feedId}/{option}
     * option = 1 | 2
     */
    @PostMapping("/{feedId}/{option}")
    public ResponseEntity<?> vote(
            @PathVariable Long feedId,
            @PathVariable int option,
            @AuthenticationPrincipal UserDetailsImpl user) {

        // 로그인 여부 검사
        if (user == null || user.getUser() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "reason", "로그인이 필요합니다."));
        }

        // 옵션 판별
        VoteOption opt = switch (option) {
            case 1 -> VoteOption.A;
            case 2 -> VoteOption.B;
            default -> null;
        };

        if (opt == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "reason", "잘못된 선택지입니다."));
        }

        try {
            // 투표 처리
            voteService.vote(feedId, user.getUser().getId(), opt);

            // 최신 요약 다시 조회
            VoteSummaryDto s = voteService.getSummary(feedId, user.getUser().getId());

            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("success", true);
            body.put("myChoice", s.getMyChoice());
            body.put("countA", s.getCountA());
            body.put("countB", s.getCountB());
            body.put("total", s.getTotal());
            body.put("ratioA", s.getRatioA());
            body.put("ratioB", s.getRatioB());
            body.put("visible", s.isVisible());
            body.put("startAt", s.getStartAt());
            body.put("endAt", s.getEndAt());

            return ResponseEntity.ok(body);

        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "reason", e.getMessage()));
        }
    }

    /**
     * ✔ 투표 상태만 조회 (예정 / 진행 중 / 마감)
     * GET /api/votes/{feedId}/state
     */
    @GetMapping("/{feedId}/state")
    public ResponseEntity<?> getState(@PathVariable Long feedId) {
        return ResponseEntity.ok(
                Map.of("state", voteService.getState(feedId).name())
        );
    }
}
