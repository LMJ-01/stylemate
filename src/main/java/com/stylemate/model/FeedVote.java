package com.stylemate.model;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "feed_votes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"feed_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ✅ 어떤 피드의 투표인지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feed_id", nullable = false)
    private Feed feed;

    /** ✅ 어떤 사용자인지 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** ✅ A / B 중 어떤 선택인지 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoteOption voteOption;

    /** ✅ 생성 시각 (투표 시각) */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** ✅ 수정 시각 (옵션 변경 시) */
    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
