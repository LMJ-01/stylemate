package com.stylemate.repository;

import com.stylemate.model.FeedVote;
import com.stylemate.model.VoteOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeedVoteRepository extends JpaRepository<FeedVote, Long> {

    // 같은 사람이 같은 피드에 투표했는지 체크
    boolean existsByFeedIdAndUserId(Long feedId, Long userId);

    // 특정 유저의 투표 조회
    Optional<FeedVote> findByFeedIdAndUserId(Long feedId, Long userId);

    // A/B 별 개수
    long countByFeedIdAndVoteOption(Long feedId, VoteOption voteOption);

    // 전체 투표 수
    long countByFeedId(Long feedId);

    // 투표 취소(필요 시)
    void deleteByFeedIdAndUserId(Long feedId, Long userId);

    // A/B 집계 한번에 (프로젝션)
    interface VoteCount {
        VoteOption getVoteOption();
        long getCount();
    }

    @Query("select v.voteOption as voteOption, count(v) as count " +
           "from FeedVote v where v.feed.id = :feedId group by v.voteOption")
    List<VoteCount> countGroupByOption(@Param("feedId") Long feedId);
}
