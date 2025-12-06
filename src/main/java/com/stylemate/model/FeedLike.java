package com.stylemate.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
public class FeedLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Feed feed;

    @ManyToOne
    private User user;

    private LocalDateTime createdAt;

    public FeedLike() {}

    public FeedLike(Feed feed, User user, LocalDateTime createdAt) {
        this.feed = feed;
        this.user = user;
        this.createdAt = createdAt;
    }

    // Getter, Setter 생략 가능 (Lombok 쓰면 @Getter/@Setter 사용 가능)
    public Long getId() {
        return id;
    }

    public Feed getFeed() {
        return feed;
    }

    public void setFeed(Feed feed) {
        this.feed = feed;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
