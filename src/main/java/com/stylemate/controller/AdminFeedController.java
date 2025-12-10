package com.stylemate.controller;

import com.stylemate.model.Comment;
import com.stylemate.model.Feed;
import com.stylemate.repository.CommentRepository;
import com.stylemate.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/feeds")
@RequiredArgsConstructor
public class AdminFeedController {

    private final FeedRepository feedRepository;
    private final CommentRepository commentRepository;

    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping
    public String page() {
        return "admin/feeds";
    }

    @GetMapping("/api")
    @ResponseBody
    public List<FeedRow> list(@RequestParam(value = "order", defaultValue = "latest") String order) {
        List<Feed> feeds = feedRepository.findAll();
        return feeds.stream()
                .sorted((a, b) -> {
                    if ("latest".equalsIgnoreCase(order)) {
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    }
                    return a.getId().compareTo(b.getId());
                })
                .map(f -> FeedRow.from(f, commentRepository.findByFeedOrderByCreatedAtDesc(f)))
                .collect(Collectors.toList());
    }

    @PatchMapping("/api/{id}/hide")
    @ResponseBody
    public ResponseEntity<?> hide(@PathVariable Long id, @RequestBody HideRequest req) {
        return feedRepository.findById(id)
                .map(f -> {
                    f.setHidden(req.hidden);
                    f.setAdminMemo(req.adminMemo);
                    feedRepository.save(f);
                    return ResponseEntity.ok(
                            FeedRow.from(f, commentRepository.findByFeedOrderByCreatedAtDesc(f))
                    );
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/api/comment/{id}/hide")
    @ResponseBody
    public ResponseEntity<?> hideComment(@PathVariable Long id, @RequestBody HideRequest req) {
        return commentRepository.findById(id)
                .map(c -> {
                    c.setHidden(req.hidden);
                    c.setAdminMemo(req.adminMemo);
                    commentRepository.save(c);
                    return ResponseEntity.ok(CommentRow.from(c));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteFeed(@PathVariable Long id) {
        return feedRepository.findById(id)
                .map(f -> {
                    feedRepository.delete(f);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/comment/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteComment(@PathVariable Long id) {
        return commentRepository.findById(id)
                .map(c -> {
                    commentRepository.delete(c);
                    return ResponseEntity.ok().build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static class HideRequest {
        public boolean hidden;
        public String adminMemo;
    }

    public static class FeedRow {
        public Long id;
        public String content;
        public String userEmail;
        public String createdAt;
        public boolean hidden;
        public String adminMemo;
        public String imageUrl;
        public String imageUrlB;
        public List<CommentRow> comments;

        public static FeedRow from(Feed f, List<Comment> comments) {
            FeedRow r = new FeedRow();
            r.id = f.getId();
            r.content = f.getContent();
            r.userEmail = f.getUser() != null ? f.getUser().getEmail() : "";
            r.createdAt = f.getCreatedAt() != null ? f.getCreatedAt().format(fmt) : "";
            r.hidden = f.isHidden();
            r.adminMemo = f.getAdminMemo();
            r.imageUrl = f.getImageUrl();
            r.imageUrlB = f.getImageUrlB();
            r.comments = comments.stream().map(CommentRow::from).collect(Collectors.toList());
            return r;
        }
    }

    public static class CommentRow {
        public Long id;
        public String content;
        public String userEmail;
        public String createdAt;
        public boolean hidden;
        public String adminMemo;

        public static CommentRow from(Comment c) {
            CommentRow r = new CommentRow();
            r.id = c.getId();
            r.content = c.getContent();
            r.userEmail = c.getUser() != null ? c.getUser().getEmail() : "";
            r.createdAt = c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "";
            r.hidden = c.isHidden();
            r.adminMemo = c.getAdminMemo();
            return r;
        }
    }
}
