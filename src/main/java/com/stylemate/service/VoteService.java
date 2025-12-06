package com.stylemate.service;

import com.stylemate.dto.VoteSummaryDto;
import com.stylemate.model.*;
import com.stylemate.repository.FeedRepository;
import com.stylemate.repository.FeedVoteRepository;
import com.stylemate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class VoteService {

    private final FeedRepository feedRepository;
    private final FeedVoteRepository feedVoteRepository;
    private final UserRepository userRepository;

    /* ===========================
       ✅ 투표하기 (A/B 선택 또는 변경)
    ============================ */
    public void vote(Long feedId, Long userId, VoteOption option) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 피드입니다."));

        if (!feed.isVote()) {
            throw new IllegalStateException("이 피드는 투표 기능이 비활성화되어 있습니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (feed.getVoteStartAt() == null || feed.getVoteEndAt() == null)
            throw new IllegalStateException("투표 시간이 설정되지 않았습니다.");
        if (now.isBefore(feed.getVoteStartAt()))
            throw new IllegalStateException("아직 투표 시작 전입니다.");
        if (!now.isBefore(feed.getVoteEndAt()))
            throw new IllegalStateException("이미 투표가 종료되었습니다.");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        Optional<FeedVote> existing = feedVoteRepository.findByFeedIdAndUserId(feedId, userId);
        if (existing.isPresent()) {
            FeedVote v = existing.get();
            v.setVoteOption(option);
            v.setUpdatedAt(LocalDateTime.now());
        } else {
            FeedVote v = new FeedVote();
            v.setFeed(feed);
            v.setUser(user);
            v.setVoteOption(option);
            feedVoteRepository.save(v);
        }
    }

    /* ===========================
       ✅ 집계 + 공개 여부 + 내 선택 반환
       - 마감 후: 모두 숫자 공개 (visible=true)
       - 진행전/진행중: 모두 마스킹 (visible=false)
    ============================ */
    @Transactional(readOnly = true)
    public VoteSummaryDto getSummary(Long feedId, Long viewerUserId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 피드입니다."));

        // 투표 비활성 피드면 항상 비공개 + 0
        if (!feed.isVote()) {
            return VoteSummaryDto.builder()
                    .countA(0).countB(0).total(0)
                    .ratioA(0).ratioB(0)
                    .visible(false)
                    .myChoice(null)
                    .startAt(feed.getVoteStartAt())
                    .endAt(feed.getVoteEndAt())
                    .build();
        }

        long a = feedVoteRepository.countByFeedIdAndVoteOption(feedId, VoteOption.A);
        long b = feedVoteRepository.countByFeedIdAndVoteOption(feedId, VoteOption.B);
        long total = a + b;

        boolean visible = (getState(feedId) == VoteState.CLOSED);  // ✅ 마감 시에만 공개

        VoteOption myChoice = null;
        if (viewerUserId != null) {
            myChoice = feedVoteRepository.findByFeedIdAndUserId(feedId, viewerUserId)
                    .map(FeedVote::getVoteOption)
                    .orElse(null);
        }

        double ratioA = total == 0 ? 0.0 : (a * 100.0) / total;
        double ratioB = total == 0 ? 0.0 : (b * 100.0) / total;

        return VoteSummaryDto.builder()
                .countA(a)
                .countB(b)
                .total(total)
                .ratioA(round1(ratioA))
                .ratioB(round1(ratioB))
                .visible(visible)
                .myChoice(myChoice)
                .startAt(feed.getVoteStartAt())
                .endAt(feed.getVoteEndAt())
                .build();
    }

    /* ===========================
       ✅ 상태 조회 (Controller에서 사용)
    ============================ */
    @Transactional(readOnly = true)
    public VoteState getState(Long feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 피드입니다."));
        return feed.getVoteState(); // Feed 엔티티에 계산 로직이 있어야 함
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
