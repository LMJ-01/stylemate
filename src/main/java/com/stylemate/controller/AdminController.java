package com.stylemate.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.stylemate.repository.UserRepository;
import com.stylemate.repository.FeedRepository;
import com.stylemate.repository.CommentRepository;
import com.stylemate.model.Feed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import java.security.Principal;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final FeedRepository feedRepository;
    private final CommentRepository commentRepository;

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model, Principal principal) {
        if (principal != null) {
            model.addAttribute("adminEmail", principal.getName());
        }
        return "admin/dashboard";
    }

    @GetMapping("/stats")
    public String statsPage() {
        return "admin/stats";
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> stats() {
        Map<String, Object> res = new HashMap<>();
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        List<Feed> feeds = feedRepository.findAll();
        List<com.stylemate.model.Comment> comments = commentRepository.findAll();

        long totalUsers = userRepository.count();
        long totalFeeds = feeds.size();
        long totalComments = comments.size();

        long todayFeeds = feeds.stream()
                .filter(f -> f.getCreatedAt() != null && !f.getCreatedAt().isBefore(start) && !f.getCreatedAt().isAfter(end))
                .count();

        long todayComments = comments.stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(start) && !c.getCreatedAt().isAfter(end))
                .count();

        Map<String, Integer> tagCount = new HashMap<>();
        for (Feed f : feeds) {
            if (f.getHashtags() == null) continue;
            String[] parts = f.getHashtags().split("[,\\s]+");
            for (String p : parts) {
                if (p == null || p.isBlank()) continue;
                String tag = p.trim();
                if (!tag.startsWith("#")) tag = "#" + tag;
                tagCount.put(tag, tagCount.getOrDefault(tag, 0) + 1);
            }
        }
        List<TagStat> topTags = tagCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> new TagStat(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // 피드 작성 TOP5
        Map<String, Integer> feedAuthorCount = new HashMap<>();
        for (Feed f : feeds) {
            String email = f.getUser() != null ? f.getUser().getEmail() : "unknown";
            feedAuthorCount.put(email, feedAuthorCount.getOrDefault(email, 0) + 1);
        }
        List<UserCountStat> topFeedAuthors = feedAuthorCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> new UserCountStat(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        // 댓글 작성 TOP5
        Map<String, Integer> commentAuthorCount = new HashMap<>();
        for (com.stylemate.model.Comment c : comments) {
            String email = c.getUser() != null ? c.getUser().getEmail() : "unknown";
            commentAuthorCount.put(email, commentAuthorCount.getOrDefault(email, 0) + 1);
        }
        List<UserCountStat> topCommentAuthors = commentAuthorCount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> new UserCountStat(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        res.put("totalUsers", totalUsers);
        res.put("totalFeeds", totalFeeds);
        res.put("totalComments", totalComments);
        res.put("todayFeeds", todayFeeds);
        res.put("todayComments", todayComments);
        res.put("topTags", topTags);
        res.put("topFeedAuthors", topFeedAuthors);
        res.put("topCommentAuthors", topCommentAuthors);
        return res;
    }

    public static class TagStat {
        public String tag;
        public Integer count;

        public TagStat(String tag, Integer count) {
            this.tag = tag;
            this.count = count;
        }
    }

    public static class UserCountStat {
        public String email;
        public Integer count;

        public UserCountStat(String email, Integer count) {
            this.email = email;
            this.count = count;
        }
    }
}
