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
     * ✅ 투표 요약 조회 (JS에서 초기화 및 새로고침 시 사용)
     * GET /api/votes/{feedId}
     */
    @GetMapping("/{feedId}")
    public ResponseEntity<?> getSummary(
            @PathVariable Long feedId,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Long viewerId = (user != null && user.getUser() != null)
                ? user.getUser().getId()
                : null;

        VoteSummaryDto s = voteService.getSummary(feedId, viewerId);
        VoteState state = voteService.getState(feedId);

        // 투표 상태 문자열
        String statusText = switch (state) {
            case SCHEDULED -> "투표 전";
            case ACTIVE -> "투표 중";
            case CLOSED -> "투표 마감";
            default -> "상태 없음"; // ✅ 누락 방지
        };

        // JS에서 카운트다운 표시용 시간 전달
        LocalDateTime start = s.getStartAt() != null ? s.getStartAt() : null;
        LocalDateTime end = s.getEndAt() != null ? s.getEndAt() : null;

        return ResponseEntity.ok(Map.of(
                "countA", s.getCountA(),
                "countB", s.getCountB(),
                "total", s.getTotal(),
                "ratioA", s.getRatioA(),
                "ratioB", s.getRatioB(),
                "visible", s.isVisible(),     // ✅ false면 JS가 ‘??’로 표시
                "myChoice", s.getMyChoice(),  // A/B/null
                "status", statusText,         // “투표 전 / 투표 중 / 투표 마감”
                "startAt", start,
                "endAt", end
        ));
    }

    /**
     * ✅ 투표하기
     * POST /api/votes/{feedId}/{option}
     * option = 1 | 2
     */
    @PostMapping("/{feedId}/{option}")
    public ResponseEntity<?> vote(
            @PathVariable Long feedId,
            @PathVariable int option,
            @AuthenticationPrincipal UserDetailsImpl user) {

        if (user == null || user.getUser() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "reason", "로그인이 필요합니다."));
        }

        VoteOption opt = (option == 1)
                ? VoteOption.A
                : (option == 2)
                ? VoteOption.B
                : null;

        if (opt == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "reason", "잘못된 옵션입니다."));
        }

        try {
            voteService.vote(feedId, user.getUser().getId(), opt);

            // 최신 요약 재계산
            VoteSummaryDto s = voteService.getSummary(feedId, user.getUser().getId());
            VoteState state = voteService.getState(feedId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "myChoice", s.getMyChoice(),
                    "countA", s.getCountA(),
                    "countB", s.getCountB(),
                    "total",  s.getTotal(),
                    "ratioA", s.getRatioA(),
                    "ratioB", s.getRatioB(),
                    "visible", s.isVisible(),
                    "startAt", s.getStartAt(),  // ✅ 추가
                    "endAt",   s.getEndAt()     // ✅ 추가
            ));

        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "reason", e.getMessage()));
        }
    }

    /**
     * ✅ 상태 조회 (선택)
     * GET /api/votes/{feedId}/state
     */
    @GetMapping("/{feedId}/state")
    public ResponseEntity<?> getState(@PathVariable Long feedId) {
        return ResponseEntity.ok(Map.of("state", voteService.getState(feedId).name()));
    }
}
