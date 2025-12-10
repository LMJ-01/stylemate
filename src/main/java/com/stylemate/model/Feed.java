package com.stylemate.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(
    name = "feeds",
    indexes = {
        @Index(name = "idx_feeds_created_at", columnList = "created_at"),
        @Index(name = "idx_feeds_user_id", columnList = "user_id"),
        @Index(name = "idx_feeds_is_vote", columnList = "is_vote"),
        @Index(name = "idx_feeds_updated_at", columnList = "updated_at")
    }
)
@Getter
@Setter
public class Feed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    /** 콤마(,)로 합쳐 저장 (예: "fashion,ootd,street") */
    @Column
    private String hashtags;

    /** A 이미지 URL */
    @Column
    private String imageUrl;

    /** B 이미지 URL (투표용) */
    @Column(name = "image_url_b")
    private String imageUrlB;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* =========================
       좋아요 / 댓글 (양방향)
       ========================= */
    @OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FeedLike> likes = new ArrayList<>();

    @OneToMany(mappedBy = "feed", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    /* =========================
       해시태그 헬퍼
       ========================= */
    @Transient
    private List<String> tagList;

    public List<String> getTagList() {
        if (tagList != null) return tagList;
        if (hashtags == null || hashtags.isBlank()) return List.of();
        return Arrays.stream(hashtags.split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .map(String::trim)
                .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
                .collect(Collectors.toList());
    }

    public void setTagList(List<String> tagList) { this.tagList = tagList; }

    /* =========================
       카운트 편의 필드
       ========================= */
    @Transient
    private int likeCount;

    @Transient
    private int commentCount;

    @Transient
    private List<Comment> commentRenderList;

    public List<Comment> getCommentsRenderList() { return commentRenderList; }
    public void setCommentsRenderList(List<Comment> comments) { this.commentRenderList = comments; }

    /* ===============================
       ✅ 투표 관련 필드
       =============================== */

    /** 투표 기능 여부 */
    @Column(name = "is_vote", nullable = false)
    private boolean vote = false;

    /** 투표 시작 / 종료 */
    @Column(name = "vote_start_at")
    private LocalDateTime voteStartAt;

    @Column(name = "vote_end_at")
    private LocalDateTime voteEndAt;

    /* ===============================
       관리자 제어
       =============================== */
    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    /** 종료 후 공개 (true = 마감 후 공개, false = 실시간 공개) */
    @Column(name = "reveal_after_end", nullable = false)
    private boolean revealAfterEnd = true; // getter: isRevealAfterEnd()

    /** 🔹 내가 선택한 옵션(1 또는 2). DB 비저장 */
    @Transient
    private Integer myChoice;

    /** 🔹 A/B 선택 수 */
    @Transient
    private int countA;

    @Transient
    private int countB;

    /** 🔹 투표 상태 계산 */
    @Transient
    public VoteState getVoteState() {
        if (!isVote() || getVoteStartAt() == null || getVoteEndAt() == null) return VoteState.SCHEDULED;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(getVoteStartAt())) return VoteState.SCHEDULED;
        if (now.isBefore(getVoteEndAt()))  return VoteState.ACTIVE;
        return VoteState.CLOSED;
    }


    /** 🔹 투표 결과 표시용: 총합 계산 */
    @Transient
    public int getTotalVotes() {
        return countA + countB;
    }

    /** 🔹 투표 퍼센트 표시 */
    @Transient
    public double getRatioA() {
        int total = getTotalVotes();
        return total == 0 ? 0 : Math.round((countA * 100.0) / total);
    }

    @Transient
    public double getRatioB() {
        int total = getTotalVotes();
        return total == 0 ? 0 : Math.round((countB * 100.0) / total);
    }

    /* =========================
       날짜 포맷(뷰 편의)
       ========================= */
    @Transient
    public String getFormattedDate() {
        if (createdAt == null) return "";
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("M월 d일 a h시 mm분", Locale.KOREAN);
        return createdAt.format(formatter);
    }
}
