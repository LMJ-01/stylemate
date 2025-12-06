package com.stylemate.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 해당 댓글이 속한 피드
    @ManyToOne
    private Feed feed;

    // 댓글을 작성한 사용자
    @ManyToOne
    private User user;

    // 댓글 내용 (500자 제한, null 불가)
    @Column(nullable = false, length = 500)
    private String content;

    // 댓글 작성 시각
    private LocalDateTime createdAt;

    // 기본 생성자 (JPA용)
    public Comment() {}

    // 필드 전체 초기화 생성자
    public Comment(Feed feed, User user, String content, LocalDateTime createdAt) {
        this.feed = feed;
        this.user = user;
        this.content = content;
        this.createdAt = createdAt;
    }
}