package com.stylemate.repository;

import com.stylemate.model.Comment;
import com.stylemate.model.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 특정 피드에 달린 모든 댓글을 최신순(내림차순)으로 조회
     * @param feed 댓글이 달린 피드
     * @return 댓글 리스트 (최신 댓글이 먼저)
     */
    List<Comment> findByFeedOrderByCreatedAtDesc(Feed feed);

    /**
     * 특정 피드에 달린 댓글 수 조회
     * @param feed 댓글이 달린 피드
     * @return 댓글 개수
     */
    long countByFeed(Feed feed);
}
