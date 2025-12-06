package com.stylemate.controller;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.model.Comment;
import com.stylemate.model.Feed;
import com.stylemate.service.CommentService;
import com.stylemate.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentService commentService;
    private final FeedService feedService;

    // 댓글 등록
    @PostMapping("/{feedId}")
    public Map<String, Object> addComment(@PathVariable Long feedId,
                                          @RequestParam String content,
                                          @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Comment comment = commentService.addComment(feedId, userDetails.getUser(), content);
        int commentCount = commentService.getComments(feedId).size();  // 댓글 수 계산

        return Map.of(
                "success", true,
                "nickname", comment.getUser().getNickname(),
                "content", comment.getContent(),
                "createdAt", comment.getCreatedAt().toString(),
                "userId", comment.getUser().getId(),
                "commentId", comment.getId(),
                "commentCount", commentCount  // ✅ 전체 댓글 수 반환
        );
    }

    // 댓글 수정
    @PostMapping("/{feedId}/{commentId}/edit")
    public Map<String, Object> editComment(@PathVariable Long feedId,
                                           @PathVariable Long commentId,
                                           @RequestParam String content,
                                           @AuthenticationPrincipal UserDetailsImpl userDetails) {
        commentService.updateComment(commentId, userDetails.getUser(), content);
        return Map.of("success", true, "content", content);
    }

    // 댓글 삭제
    @DeleteMapping("/{feedId}/{commentId}")
    public Map<String, Object> deleteComment(@PathVariable Long feedId,
                                             @PathVariable Long commentId,
                                             @AuthenticationPrincipal UserDetailsImpl userDetails) {
        commentService.deleteComment(commentId, userDetails.getUser());
        int commentCount = commentService.getComments(feedId).size();  // 삭제 후 댓글 수 반환
        return Map.of("success", true, "commentCount", commentCount);
    }

    // 페이징 처리된 댓글 목록 - 최신순 정렬
    @GetMapping("/{feedId}")
    public List<Map<String, Object>> getComments(@PathVariable Long feedId,
                                                 @RequestParam(defaultValue = "0") int offset,
                                                 @RequestParam(defaultValue = "3") int limit) {
        List<Comment> allComments = commentService.getComments(feedId);
        allComments.sort(Comparator.comparing(Comment::getCreatedAt).reversed());  // 최신순 정렬

        int toIndex = Math.min(offset + limit, allComments.size());
        List<Comment> paged = allComments.subList(offset, toIndex);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment comment : paged) {
            result.add(Map.of(
                    "id", comment.getId(),
                    "nickname", comment.getUser().getNickname(),
                    "content", comment.getContent(),
                    "createdAt", comment.getCreatedAt().toString(),
                    "userId", comment.getUser().getId()
            ));
        }
        return result;
    }
}
