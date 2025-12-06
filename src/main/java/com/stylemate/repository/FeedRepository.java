package com.stylemate.repository;

import com.stylemate.model.Feed;
import com.stylemate.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedRepository extends JpaRepository<Feed, Long> {

    /* ===== 개별 조회 ===== */
    List<Feed> findByUser(User user);

    @Query("SELECT f FROM Feed f WHERE f.user.id = :userId")
    List<Feed> findByUserId(@Param("userId") Long userId);

    List<Feed> findByUserIdOrderByCreatedAtDesc(Long userId);

    /* ===== 목록 정렬/검색 ===== */

    // 최신/오래된 순 (생성일 기준)
    List<Feed> findAllByOrderByCreatedAtDesc();
    List<Feed> findAllByOrderByCreatedAtAsc();

    // 최신 N개 (PageRequest.of(0, n) 사용)
    List<Feed> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // ✅ 최신순 (updatedAt 우선, 없으면 createdAt)
    @Query("SELECT f FROM Feed f ORDER BY COALESCE(f.updatedAt, f.createdAt) DESC")
    List<Feed> findAllOrderByUpdatedDesc();

    // ✅ 좋아요 많은 순 (+ 동점 시 최신)
    @Query("SELECT f FROM Feed f LEFT JOIN f.likes l GROUP BY f ORDER BY COUNT(l) DESC, COALESCE(f.updatedAt, f.createdAt) DESC")
    List<Feed> findAllOrderByLikeCountDesc();

    // ✅ 댓글 많은 순 (+ 동점 시 최신)
    @Query("SELECT f FROM Feed f LEFT JOIN f.comments c GROUP BY f ORDER BY COUNT(c) DESC, COALESCE(f.updatedAt, f.createdAt) DESC")
    List<Feed> findAllOrderByCommentCountDesc();

    // 태그 검색: 기본/대소문자 무시 둘 다 제공
    List<Feed> findByHashtagsContaining(String tag);
    List<Feed> findByHashtagsContainingIgnoreCase(String tag);

    /* (선택) Native Top-N */
    @Query(value = "SELECT * FROM feeds ORDER BY created_at DESC LIMIT :count", nativeQuery = true)
    List<Feed> findTopNByOrderByCreatedAtDesc(@Param("count") int count);
}
