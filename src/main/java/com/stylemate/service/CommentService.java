package com.stylemate.service;

import com.stylemate.model.Comment;
import com.stylemate.model.Feed;
import com.stylemate.model.User;
import com.stylemate.repository.CommentRepository;
import com.stylemate.repository.FeedRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final FeedRepository feedRepository;

    /**
     * 댓글 추가
     * @param feedId 피드 ID
     * @param user 댓글 작성자
     * @param content 댓글 내용
     * @return 저장된 댓글
     */
    @Transactional
    public Comment addComment(Long feedId, User user, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("❌ 댓글 내용이 비어있습니다.");
        }

        Feed feed = getFeedOrThrow(feedId);
        Comment comment = new Comment(feed, user, content.trim(), LocalDateTime.now());
        return commentRepository.save(comment);
    }

    /**
     * 댓글 삭제 (작성자 또는 관리자만 가능)
     * @param commentId 댓글 ID
     * @param user 현재 사용자
     */
    @Transactional
    public void deleteComment(Long commentId, User user) {
        Comment comment = getCommentOrThrow(commentId);

        boolean isOwner = comment.getUser().getId().equals(user.getId());
        boolean isAdmin = "admin@stylemate.com".equals(user.getEmail());

        if (!isOwner && !isAdmin) {
            throw new SecurityException("❌ 댓글 삭제 권한이 없습니다.");
        }

        commentRepository.delete(comment);
    }

    /**
     * 댓글 수정 (작성자만 가능)
     * @param commentId 댓글 ID
     * @param user 현재 사용자
     * @param newContent 수정할 내용
     */
    @Transactional
    public void updateComment(Long commentId, User user, String newContent) {
        if (newContent == null || newContent.trim().isEmpty()) {
            throw new IllegalArgumentException("❌ 댓글 내용이 비어있습니다.");
        }

        Comment comment = getCommentOrThrow(commentId);

        if (!comment.getUser().getId().equals(user.getId())) {
            throw new SecurityException("❌ 댓글 수정 권한이 없습니다.");
        }

        comment.setContent(newContent.trim());
        commentRepository.save(comment);
    }

    /**
     * 특정 피드의 모든 댓글 조회 (최신순)
     * @param feedId 피드 ID
     * @return 댓글 리스트
     */
    public List<Comment> getComments(Long feedId) {
        Feed feed = getFeedOrThrow(feedId);
        return commentRepository.findByFeedOrderByCreatedAtDesc(feed);
    }

    /**
     * 특정 댓글 단건 조회
     * @param commentId 댓글 ID
     * @return 댓글 객체
     */
    public Comment findById(Long commentId) {
        return getCommentOrThrow(commentId);
    }

    /**
     * 특정 피드에 달린 댓글 수 반환
     * @param feedId 피드 ID
     * @return 댓글 수
     */
    public int getCommentCountByFeedId(Long feedId) {
        Feed feed = getFeedOrThrow(feedId);
        return Math.toIntExact(commentRepository.countByFeed(feed));
    }

    /**
     * 피드 ID로 피드 객체 조회 (없으면 예외)
     */
    private Feed getFeedOrThrow(Long feedId) {
        return feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("❌ 피드를 찾을 수 없습니다. ID: " + feedId));
    }

    /**
     * 댓글 ID로 댓글 객체 조회 (없으면 예외)
     */
    private Comment getCommentOrThrow(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("❌ 댓글을 찾을 수 없습니다. ID: " + commentId));
    }
}
