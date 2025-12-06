package com.stylemate.repository;

import com.stylemate.model.Feed;
import com.stylemate.model.FeedLike;
import com.stylemate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedLikeRepository extends JpaRepository<FeedLike, Long> {
    boolean existsByFeedAndUser(Feed feed, User user);
    Optional<FeedLike> findByFeedAndUser(Feed feed, User user);
    long countByFeed(Feed feed);
}
