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

    @ManyToOne
    private Feed feed;

    @ManyToOne
    private User user;

    @Column(nullable = false, length = 500)
    private String content;

    private LocalDateTime createdAt;

    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    @Column(name = "admin_memo", length = 500)
    private String adminMemo;

    public Comment() {}

    public Comment(Feed feed, User user, String content, LocalDateTime createdAt) {
        this.feed = feed;
        this.user = user;
        this.content = content;
        this.createdAt = createdAt;
    }
}
