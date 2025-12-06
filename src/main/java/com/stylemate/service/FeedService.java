package com.stylemate.service;

import com.stylemate.model.Feed;
import com.stylemate.model.FeedLike;
import com.stylemate.model.User;
import com.stylemate.model.FeedVote;
import com.stylemate.model.VoteOption;
import com.stylemate.repository.FeedLikeRepository;
import com.stylemate.repository.FeedRepository;
import com.stylemate.repository.CommentRepository;
import com.stylemate.repository.FeedVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File; // (구) updateFeed 유지용
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final CommentRepository commentRepository;
    private final FeedVoteRepository feedVoteRepository;

    /* ========== 생성/조회/저장 ========== */

    public Feed saveFeed(Feed feed) {
        normalizeTagsOnEntity(feed);
        return feedRepository.save(feed);
    }

    public Feed createFeed(Feed feed, User user) {
        feed.setUser(user);
        normalizeTagsOnEntity(feed);
        return feedRepository.save(feed);
    }

    /** 컨트롤러에서 엔티티를 수정한 뒤 그대로 저장할 때 사용 */
    public Feed updateFeedEntity(Feed feed) {
        normalizeTagsOnEntity(feed);
        return feedRepository.save(feed);
    }

    @Transactional(readOnly = true)
    public List<Feed> getAllFeeds() {
        return feedRepository.findAll();
    }

    /** ✅ 최신 N개: updatedAt 우선, 없으면 createdAt — 수정 후 목록에서 ‘사라짐’ 방지 */
    @Transactional(readOnly = true)
    public List<Feed> getLatestFeeds(int count) {
        List<Feed> all = feedRepository.findAll();
        return all.stream()
                .sorted((a, b) -> {
                    LocalDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                    LocalDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                    return tb.compareTo(ta);
                })
                .limit(count)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Feed getFeedById(Long id) {
        return feedRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("피드를 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Feed> getFeedsByUser(User user) {
        return feedRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public List<Feed> getFeedsByUserId(Long userId) {
        // ✅ 최근 수정/생성 순으로 정렬
        List<Feed> feeds = feedRepository.findByUserId(userId);
        feeds.sort((a, b) -> {
            LocalDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            LocalDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            return tb.compareTo(ta);
        });
        return feeds;
    }

    /* ========== 정렬/검색 ========== */

    /** ✅ 최근순: updatedAt 우선 */
    @Transactional(readOnly = true)
    public List<Feed> getFeedsOrderByRecent() {
        List<Feed> feeds = feedRepository.findAll();
        feeds.sort((a, b) -> {
            LocalDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            LocalDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            return tb.compareTo(ta);
        });
        return feeds;
    }

    @Transactional(readOnly = true)
    public List<Feed> getFeedsOrderByOldest() {
        List<Feed> feeds = feedRepository.findAll();
        feeds.sort(Comparator.comparing(Feed::getCreatedAt));
        return feeds;
    }

    /** ✅ 좋아요순: 실시간 집계 후 정렬 */
    @Transactional(readOnly = true)
    public List<Feed> getFeedsOrderByLikes() {
        List<Feed> feeds = feedRepository.findAll();
        feeds.forEach(f -> f.setLikeCount(getLikeCount(f.getId())));
        feeds.sort((a, b) -> Integer.compare(b.getLikeCount(), a.getLikeCount()));
        return feeds;
    }

    /** ✅ 댓글순: 실시간 집계 후 정렬 */
    @Transactional(readOnly = true)
    public List<Feed> getFeedsOrderByComments() {
        List<Feed> feeds = feedRepository.findAll();
        feeds.forEach(f -> f.setCommentCount(getCommentCount(f.getId())));
        feeds.sort((a, b) -> Integer.compare(b.getCommentCount(), a.getCommentCount()));
        return feeds;
    }

    @Transactional(readOnly = true)
    public List<Feed> getFeedsByHashtag(String tag) {
        if (tag == null || tag.isBlank()) return List.of();
        List<Feed> feeds = feedRepository.findByHashtagsContaining(tag);
        feeds.sort((a, b) -> {
            LocalDateTime ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            LocalDateTime tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            return tb.compareTo(ta);
        });
        return feeds;
    }

    /* ========== 좋아요/댓글 카운트 ========== */

    public boolean toggleLike(Long feedId, User user) {
        Feed feed = getFeedById(feedId);
        Optional<FeedLike> existing = feedLikeRepository.findByFeedAndUser(feed, user);
        if (existing.isPresent()) {
            feedLikeRepository.delete(existing.get());
            return false;
        } else {
            FeedLike like = new FeedLike();
            like.setFeed(feed);
            like.setUser(user);
            like.setCreatedAt(LocalDateTime.now());
            feedLikeRepository.save(like);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasUserLiked(Long feedId, User user) {
        Feed feed = getFeedById(feedId);
        return feedLikeRepository.findByFeedAndUser(feed, user).isPresent();
    }

    @Transactional(readOnly = true)
    public int getLikeCount(Long feedId) {
        Feed feed = getFeedById(feedId);
        return (int) feedLikeRepository.countByFeed(feed);
    }

    @Transactional(readOnly = true)
    public int getCommentCount(Long feedId) {
        Feed feed = getFeedById(feedId);
        return (int) commentRepository.countByFeed(feed);
    }

    /* ========== 삭제 ========== */

    public void deleteFeed(Long id, User user) {
        Feed feed = getFeedById(id);
        if (!feed.getUser().getId().equals(user.getId()) &&
            !user.getEmail().equalsIgnoreCase("admin@stylemate.com")) {
            throw new SecurityException("삭제 권한이 없습니다.");
        }
        feedRepository.delete(feed);
    }

    /* ========== 투표 기능 ========== */

    /** int option(1|2) → VoteOption */
    private VoteOption toVoteOption(int option) {
        if (option == 1) return VoteOption.A;
        if (option == 2) return VoteOption.B;
        throw new IllegalArgumentException("invalid option: " + option);
    }

    /** 내가 고른 옵션 → 1|2 (없으면 null) */
    private Integer toIntOption(VoteOption opt) {
        if (opt == null) return null;
        return (opt == VoteOption.A) ? 1 : 2;
    }

    /** ✅ 유저 투표 생성/변경 (컨트롤러와 시그니처 일치) */
    public boolean castVote(Long feedId, User user, int option) {
        VoteOption vo = toVoteOption(option);
        Feed feed = getFeedById(feedId);

        Optional<FeedVote> existing = feedVoteRepository.findByFeedIdAndUserId(feedId, user.getId());
        if (existing.isPresent()) {
            FeedVote v = existing.get();
            if (vo.equals(v.getVoteOption())) {
                return true; // 동일 선택이면 그대로 성공 처리
            }
            v.setVoteOption(vo);
            v.setUpdatedAt(LocalDateTime.now());
            return true;
        } else {
            FeedVote v = new FeedVote();
            v.setFeed(feed);
            v.setUser(user);
            v.setVoteOption(vo);
            v.setCreatedAt(LocalDateTime.now());
            feedVoteRepository.save(v);
            return true;
        }
    }

    /** ✅ 항상 실제 A/B 개수를 반환 (프런트에서 마스킹 여부 결정) */
    @Transactional(readOnly = true)
    public Map<String, Integer> getVoteCountsRaw(Long feedId) {
        int a = (int) feedVoteRepository.countByFeedIdAndVoteOption(feedId, VoteOption.A);
        int b = (int) feedVoteRepository.countByFeedIdAndVoteOption(feedId, VoteOption.B);
        return Map.of("a", a, "b", b);
    }

    /** (호환용) 기존 메서드도 RAW를 그대로 반환하도록 통일 */
    @Transactional(readOnly = true)
    public Map<String, Integer> getVoteCounts(Long feedId) {
        return getVoteCountsRaw(feedId);
    }

    /** ✅ 내 선택 (없으면 null) — 초기 렌더 하이라이트용 */
    @Transactional(readOnly = true)
    public Integer getMyVote(Long feedId, Long userId) {
        return feedVoteRepository.findByFeedIdAndUserId(feedId, userId)
                .map(FeedVote::getVoteOption)
                .map(this::toIntOption)
                .orElse(null);
    }

    /* ========== (호환) (구) update 메서드 유지 ========== */

    @Deprecated
    public void updateFeed(Long id, String content, String tags, org.springframework.web.multipart.MultipartFile imageFile, Long userId) {
        Feed feed = getFeedById(id);
        if (!feed.getUser().getId().equals(userId)) {
            throw new SecurityException("수정 권한이 없습니다.");
        }

        feed.setContent(content);
        // 태그 동기화
        if (tags == null || tags.isBlank()) {
            feed.setTagList(List.of());
            feed.setHashtags("");
        } else {
            List<String> tagList = toTagList(tags);
            feed.setTagList(tagList);
            feed.setHashtags(String.join(",", tagList));
        }

        // (구) 이미지 업로드 로직 그대로 유지
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String fileName = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
                String uploadDir = System.getProperty("user.home") + "/stylemate-uploads";
                File folder = new File(uploadDir);
                if (!folder.exists()) folder.mkdirs();
                File saveFile = new File(uploadDir, fileName);
                imageFile.transferTo(saveFile);
                feed.setImageUrl("/uploads/" + fileName);
            } catch (Exception e) {
                throw new RuntimeException("이미지 업로드 중 오류 발생", e);
            }
        }

        normalizeTagsOnEntity(feed);
        feedRepository.save(feed);
    }

    /* ========== 내부 유틸 ========== */

    /** "a, b , c" → ["a","b","c"] (trim/빈값 제거/중복 제거) */
    private List<String> toTagList(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split("\\s*,\\s*"))
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    /** 엔티티 내부의 hashtags ↔ tagList를 일관성 있게 맞춤 */
    private void normalizeTagsOnEntity(Feed feed) {
        // 우선순위: tagList가 있으면 그것을 기준으로 hashtags 생성
        if (feed.getTagList() != null && !feed.getTagList().isEmpty()) {
            List<String> cleaned = feed.getTagList().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
            feed.setTagList(cleaned);
            feed.setHashtags(String.join(",", cleaned));
            return;
        }

        // tagList가 비어있으면 hashtags를 분해해서 채움
        List<String> fromHashtags = toTagList(feed.getHashtags());
        feed.setTagList(fromHashtags);
        feed.setHashtags(String.join(",", fromHashtags));
    }
}
