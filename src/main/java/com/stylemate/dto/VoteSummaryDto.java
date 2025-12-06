package com.stylemate.dto;

import com.stylemate.model.VoteOption;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class VoteSummaryDto {
    private long countA;
    private long countB;
    private long total;
    private double ratioA;
    private double ratioB;
    private boolean visible;
    private VoteOption myChoice;

    // ✅ 투표 시작/종료 시간 추가
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
