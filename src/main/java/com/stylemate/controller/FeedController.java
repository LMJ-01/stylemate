package com.stylemate.controller;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.model.Comment;
import com.stylemate.model.Feed;
import com.stylemate.service.CommentService;
import com.stylemate.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/feed")
public class FeedController {

    private final FeedService feedService;
    private final CommentService commentService;

    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("user.home"), "stylemate-uploads");
    private static final DateTimeFormatter DTF_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** ✅ 로그인 사용자 공통 속성 주입 */
    @ModelAttribute
    public void injectLoginUser(Model model, @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails != null && userDetails.getUser() != null) {
            model.addAttribute("loginUserId", userDetails.getUser().getId());
            model.addAttribute("nickname", userDetails.getUser().getNickname());
        } else {
            model.addAttribute("loginUserId", null);
            model.addAttribute("nickname", null);
        }
    }

    /** ✅ voteStartAt / voteEndAt 자동바인딩 방지 */
    @InitBinder("feed")
    public void initBinder(WebDataBinder binder) {
        binder.setDisallowedFields("voteStartAt", "voteEndAt");
    }

    /** ✅ 피드 작성 폼 */
    @GetMapping("/new")
    public String showFeedForm(Model model) {
        model.addAttribute("feed", new Feed());
        return "feed/new";
    }

    /** ✅ 피드 생성 */
    @PostMapping("/create")
    public String createFeed(@ModelAttribute Feed feed,
                             @RequestParam(value = "imageFile", required = false) MultipartFile imageFileA,
                             @RequestParam(value = "imageFileB", required = false) MultipartFile imageFileB,
                             @RequestParam(value = "vote", required = false) Boolean vote,
                             @RequestParam(value = "voteStartAtStr", required = false) String voteStartAtStr,
                             @RequestParam(value = "voteEndAtStr", required = false) String voteEndAtStr,
                             @RequestParam(value = "revealAfterEnd", required = false) Boolean revealAfterEnd,
                             @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) return "redirect:/user/login";

        try {
            ensureUploadDir();

            String imageUrlA = storeFileOrNull(imageFileA);
            String imageUrlB = (Boolean.TRUE.equals(vote) ? storeFileOrNull(imageFileB) : null);

            feed.setUser(userDetails.getUser());
            feed.setImageUrl(imageUrlA);

            applyVoteFields(feed, vote, revealAfterEnd, voteStartAtStr, voteEndAtStr, imageUrlB, false);
            feedService.createFeed(feed, userDetails.getUser());
            return "redirect:/feed/list";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/feed/new?error=true";
        }
    }

    /** ✅ 전체 피드 */
    @GetMapping("/list")
    public String feedList(Model model,
                           @RequestParam(value = "sort", defaultValue = "recent") String sort,
                           @RequestParam(value = "tag", required = false) String tag,
                           @AuthenticationPrincipal UserDetailsImpl userDetails) {

        List<Feed> feeds = (tag != null && !tag.isBlank())
                ? feedService.getFeedsByHashtag(tag)
                : switch (sort) {
                    case "likes" -> feedService.getFeedsOrderByLikes();
                    case "comments" -> feedService.getFeedsOrderByComments();
                    case "old" -> feedService.getFeedsOrderByOldest();
                    default -> feedService.getFeedsOrderByRecent();
                };

        Long loginUserId = (userDetails != null && userDetails.getUser() != null)
                ? userDetails.getUser().getId() : null;

        enrichFeedsWithCounts(feeds, loginUserId);

        model.addAttribute("feeds", feeds);
        model.addAttribute("viewingUserId", null);
        return "feed/feeds";
    }

    /** ✅ 피드 수정 폼 */
    @GetMapping("/edit/{id}")
    public String editFeedForm(@PathVariable Long id, Model model,
                               @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null)
            return "redirect:/user/login";

        Feed feed = feedService.getFeedById(id);
        if (feed == null) return "redirect:/feed/list?error=notfound";
        if (!feed.getUser().getId().equals(userDetails.getUser().getId()))
            throw new AccessDeniedException("수정 권한이 없습니다.");

        model.addAttribute("feed", feed);
        return "feed/edit";
    }

    /** ✅ 피드 수정 */
    @PostMapping("/edit/{id}")
    public String updateFeed(@PathVariable Long id,
                             @RequestParam String content,
                             @RequestParam(required = false) String tags,
                             @RequestParam(value = "imageFile", required = false) MultipartFile imageFileA,
                             @RequestParam(value = "imageFileB", required = false) MultipartFile imageFileB,
                             @RequestParam(value = "vote", required = false) Boolean vote,
                             @RequestParam(value = "voteStartAtStr", required = false) String voteStartAtStr,
                             @RequestParam(value = "voteEndAtStr", required = false) String voteEndAtStr,
                             @RequestParam(value = "revealAfterEnd", required = false) Boolean revealAfterEnd,
                             @AuthenticationPrincipal UserDetailsImpl userDetails) {

        if (userDetails == null || userDetails.getUser() == null)
            return "redirect:/user/login";

        try {
            ensureUploadDir();

            Feed existing = feedService.getFeedById(id);
            if (existing == null) return "redirect:/feed/list?error=notfound";
            if (!existing.getUser().getId().equals(userDetails.getUser().getId()))
                throw new AccessDeniedException("수정 권한이 없습니다.");

            String newImageUrlA = storeFileOrNull(imageFileA);
            if (newImageUrlA != null) existing.setImageUrl(newImageUrlA);

            existing.setContent(content);
            existing.setHashtags(tags);

            String newImageUrlB = (Boolean.TRUE.equals(vote) ? storeFileOrNull(imageFileB) : null);
            applyVoteFields(existing, vote, revealAfterEnd, voteStartAtStr, voteEndAtStr, newImageUrlB, true);

            feedService.updateFeedEntity(existing);
            return "redirect:/feed/list";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/feed/edit/" + id + "?error=true";
        }
    }

    /** ✅ 피드 삭제 */
    @PostMapping("/delete/{id}")
    public String deleteFeed(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null)
            return "redirect:/user/login";

        try {
            feedService.deleteFeed(id, userDetails.getUser());
            return "redirect:/feed/list";
        } catch (SecurityException e) {
            return "redirect:/feed/list?error=unauthorized";
        }
    }

    /** ✅ 좋아요 (AJAX) */
    @PostMapping(value = "/{id}/like", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> likeFeed(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null)
            return Map.of("liked", false, "likeCount", feedService.getLikeCount(id));

        boolean liked = feedService.toggleLike(id, userDetails.getUser());
        int likeCount = feedService.getLikeCount(id);
        return Map.of("liked", liked, "likeCount", likeCount);
    }

    /** ✅ 투표하기 (AJAX) */
    @PostMapping(value = "/{id}/vote", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> vote(@PathVariable Long id,
                                    @RequestParam("option") int option,
                                    @AuthenticationPrincipal UserDetailsImpl userDetails) {

        if (userDetails == null || userDetails.getUser() == null)
            return Map.of("success", false, "reason", "unauthenticated");

        Feed feed = feedService.getFeedById(id);
        if (feed == null || !feed.isVote())
            return Map.of("success", false, "reason", "not-votable");

        LocalDateTime now = LocalDateTime.now();
        if (feed.getVoteStartAt() != null && now.isBefore(feed.getVoteStartAt()))
            return Map.of("success", false, "reason", "not-started");
        if (feed.getVoteEndAt() != null && now.isAfter(feed.getVoteEndAt()))
            return Map.of("success", false, "reason", "ended");

        if (option != 1 && option != 2)
            return Map.of("success", false, "reason", "invalid-option");

        boolean accepted = feedService.castVote(id, userDetails.getUser(), option);
        Map<String, Integer> counts = feedService.getVoteCounts(id);

        int a = counts.getOrDefault("a", 0);
        int b = counts.getOrDefault("b", 0);
        int total = a + b;

        return Map.of(
                "success", accepted,
                "myChoice", option,
                "a", a, "b", b, "total", total,
                "revealAfterEnd", feed.isRevealAfterEnd()
        );
    }

    /** ✅ 투표 상태/요약 (항상 실제 카운트 반환) */
    @GetMapping(value = "/{id}/vote/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> getVoteSummary(@PathVariable Long id,
                                              @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Feed feed = feedService.getFeedById(id);
        if (feed == null || !feed.isVote()) {
            return Map.of("status", "-", "remaining", "-", "canVote", false, "a", 0, "b", 0);
        }

        LocalDateTime now   = LocalDateTime.now();
        LocalDateTime start = feed.getVoteStartAt();
        LocalDateTime end   = feed.getVoteEndAt();

        String status;
        String remaining;
        boolean canVote;

        if (start != null && now.isBefore(start)) {
            status = "투표 전";
            remaining = formatDuration(Duration.between(now, start));
            canVote = false;
        } else if (end != null && now.isAfter(end)) {
            status = "투표 마감";
            remaining = "마감됨";
            canVote = false;
        } else {
            status = "투표 중";
            remaining = (end != null) ? formatDuration(Duration.between(now, end)) : "-";
            canVote = true;
        }

        // ✅ 항상 전체 투표 수를 반환 (비투표자도 동일하게 본다)
        Map<String, Integer> counts = feedService.getVoteCounts(id);
        int a = counts.getOrDefault("a", 0);
        int b = counts.getOrDefault("b", 0);

        Integer myChoice = null;
        if (userDetails != null && userDetails.getUser() != null) {
            myChoice = feedService.getMyVote(id, userDetails.getUser().getId());
        }

        // ✅ 투표 중이라도 실제 카운트는 내려주지만,
        //    프론트는 maskCounts=true로 ??표로 표시함
        return Map.of(
                "status", status,
                "remaining", remaining,
                "canVote", canVote,
                "a", a,
                "b", b,
                "startAt", start != null ? start.toString() : null,
                "endAt",   end   != null ? end.toString()   : null,
                "myChoice", myChoice,
                "revealAfterEnd", feed.isRevealAfterEnd()
        );
    }

    /** ✅ Duration → 남은 시간 문자열 변환 */
    private String formatDuration(Duration d) {
        if (d.isNegative() || d.isZero()) return "곧 시작";
        long h = d.toHours();
        long m = d.minusHours(h).toMinutes();
        if (h > 0) return h + "시간 " + m + "분";
        else if (m > 0) return m + "분";
        else return "곧 시작";
    }

    // ---------------- 댓글: 작성/수정/삭제 (폼/JSON 겸용) ---------------- //

    /** ✅ 댓글 작성: x-www-form-urlencoded */
    @PostMapping(value = "/{feedId}/comment", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> addCommentForm(@PathVariable Long feedId,
                                              @RequestParam String content,
                                              @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return addCommentCore(feedId, content, userDetails);
    }

    /** ✅ 댓글 작성: application/json */
    @PostMapping(value = "/{feedId}/comment", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> addCommentJson(@PathVariable Long feedId,
                                              @RequestBody Map<String, String> body,
                                              @AuthenticationPrincipal UserDetailsImpl userDetails) {
        String content = Optional.ofNullable(body.get("content")).orElse("");
        return addCommentCore(feedId, content, userDetails);
    }

    /** ✅ 공통 댓글 작성 로직 */
    private Map<String, Object> addCommentCore(Long feedId, String content, UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return Map.of("success", false, "reason", "unauthenticated");
        }
        Comment newComment = commentService.addComment(feedId, userDetails.getUser(), content);
        return Map.of(
                "success", true,
                "nickname", newComment.getUser().getNickname(),
                "content", newComment.getContent(),
                "createdAt", newComment.getCreatedAt().toString(),
                "userId", newComment.getUser().getId(),
                "commentId", newComment.getId()
        );
    }

    /** ✅ 댓글 수정: x-www-form-urlencoded */
    @PostMapping(value = "/{feedId}/comment/{commentId}/edit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> updateCommentForm(@PathVariable Long feedId,
                                                 @PathVariable Long commentId,
                                                 @RequestParam String content,
                                                 @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return Map.of("success", false, "reason", "unauthenticated");
        }
        commentService.updateComment(commentId, userDetails.getUser(), content);
        return Map.of("success", true, "content", content);
    }

    /** ✅ 댓글 수정: application/json */
    @PostMapping(value = "/{feedId}/comment/{commentId}/edit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> updateCommentJson(@PathVariable Long feedId,
                                                 @PathVariable Long commentId,
                                                 @RequestBody Map<String, String> body,
                                                 @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return Map.of("success", false, "reason", "unauthenticated");
        }
        String content = Optional.ofNullable(body.get("content")).orElse("");
        commentService.updateComment(commentId, userDetails.getUser(), content);
        return Map.of("success", true, "content", content);
    }

    /** ✅ 댓글 삭제 */
    @PostMapping(value = "/{feedId}/comment/{commentId}/delete", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> deleteComment(@PathVariable Long feedId,
                                             @PathVariable Long commentId,
                                             @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return Map.of("success", false, "reason", "unauthenticated");
        }
        commentService.deleteComment(commentId, userDetails.getUser());
        return Map.of("success", true, "commentId", commentId);
    }

    /** ✅ 모달용 피드 상세 */
    @GetMapping("/modal/{id}")
    public String getFeedModal(@PathVariable Long id,
                               Model model,
                               @AuthenticationPrincipal UserDetailsImpl userDetails) {
        Feed feed = feedService.getFeedById(id);
        if (feed == null) {
            return "feed/modal :: modalContent";
        }
        feed.setLikeCount(feedService.getLikeCount(id));
        List<Comment> comments = commentService.getComments(id);
        feed.setCommentCount(comments.size());
        feed.setComments(comments);

        Long loginUserId = (userDetails != null && userDetails.getUser() != null)
                ? userDetails.getUser().getId() : null;
        if (loginUserId != null) {
            Integer choice = feedService.getMyVote(feed.getId(), loginUserId);
            feed.setMyChoice(choice);
        }

        model.addAttribute("feed", feed);
        return "feed/modal :: modalContent";
    }

    /** ✅ 특정 사용자 피드 */
    @GetMapping("/user/{userId}")
    public String getFeedsByUser(@PathVariable Long userId,
                                 Model model,
                                 @AuthenticationPrincipal UserDetailsImpl userDetails) {
        List<Feed> feeds = feedService.getFeedsByUserId(userId);

        Long loginUserId = (userDetails != null && userDetails.getUser() != null)
                ? userDetails.getUser().getId() : null;
        enrichFeedsWithCounts(feeds, loginUserId);

        model.addAttribute("feeds", feeds);
        model.addAttribute("viewingUserId", userId);
        return "feed/feeds";
    }

    /** ✅ 내 피드 */
    @GetMapping("/my")
    public String myFeeds(Model model,
                          @AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null || userDetails.getUser() == null) {
            return "redirect:/user/login";
        }
        Long userId = userDetails.getUser().getId();
        List<Feed> feeds = feedService.getFeedsByUserId(userId);

        enrichFeedsWithCounts(feeds, userId);

        model.addAttribute("feeds", feeds);
        model.addAttribute("viewingUserId", userId);
        return "feed/feeds";
    }

    // ===================== 🔧 유틸 =====================

    /** like/comment 카운트 + (로그인 시) 내 투표 선택 주입 */
    private void enrichFeedsWithCounts(List<Feed> feeds, Long loginUserId) {
        for (Feed f : feeds) {
            f.setLikeCount(feedService.getLikeCount(f.getId()));
            List<Comment> comments = commentService.getComments(f.getId());
            f.setCommentCount(comments.size());
            f.setComments(comments);
            if (loginUserId != null) {
                Integer choice = feedService.getMyVote(f.getId(), loginUserId);
                f.setMyChoice(choice);
            }
        }
    }

    private LocalDateTime parseDatetimeLocal(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDateTime.parse(v, DTF_MIN);
        } catch (Exception e1) {
            try {
                return LocalDateTime.parse(v);
            } catch (Exception e2) {
                System.out.println("[WARN] 잘못된 날짜 형식: " + v);
                return null;
            }
        }
    }

    private void applyVoteFields(Feed feed, Boolean vote, Boolean revealAfterEnd,
                                 String voteStartAtStr, String voteEndAtStr,
                                 String imageUrlB, boolean isUpdate) {
        if (vote != null) feed.setVote(vote);
        if (revealAfterEnd != null) feed.setRevealAfterEnd(revealAfterEnd);
        else if (!isUpdate) feed.setRevealAfterEnd(true);

        if (!feed.isVote()) {
            feed.setImageUrlB(null);
            feed.setVoteStartAt(null);
            feed.setVoteEndAt(null);
            feed.setRevealAfterEnd(true);
            return;
        }

        if (imageUrlB != null) feed.setImageUrlB(imageUrlB);
        else if (!isUpdate && feed.getImageUrlB() == null) {
            feed.setVote(false);
            feed.setRevealAfterEnd(true);
            feed.setVoteStartAt(null);
            feed.setVoteEndAt(null);
            return;
        }

        LocalDateTime start = parseDatetimeLocal(voteStartAtStr);
        LocalDateTime end   = parseDatetimeLocal(voteEndAtStr);

        if (!isUpdate) {
            if (start == null) start = LocalDateTime.now();
            if (end == null)   end   = start.plusHours(24);
        } else {
            if (start == null) start = feed.getVoteStartAt();
            if (end == null)   end   = feed.getVoteEndAt();
        }

        if (start == null || end == null || !start.isBefore(end)) {
            if (isUpdate) return;
            feed.setVote(false);
            feed.setRevealAfterEnd(true);
            feed.setImageUrlB(null);
            feed.setVoteStartAt(null);
            feed.setVoteEndAt(null);
            return;
        }

        feed.setVoteStartAt(start);
        feed.setVoteEndAt(end);
    }

    private void ensureUploadDir() throws IOException {
        if (Files.notExists(UPLOAD_DIR)) Files.createDirectories(UPLOAD_DIR);
    }

    private String storeFileOrNull(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;

        String original = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
        String cleaned = original.replaceAll("[^A-Za-z0-9._-]", "_");
        String filename = UUID.randomUUID() + "_" + cleaned;

        Path target = UPLOAD_DIR.resolve(filename).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + filename;
    }
}