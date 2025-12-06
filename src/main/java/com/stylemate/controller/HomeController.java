package com.stylemate.controller;

import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.model.Feed;
import com.stylemate.service.FeedService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class HomeController {

    private final FeedService feedService;

    @GetMapping("/")
    public String root() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        // ✅ 비로그인은 /user/login 으로
        return loggedIn ? "redirect:/home" : "redirect:/user/login";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal UserDetailsImpl userDetails, Model model) {

        Long loginUserId = null;
        String nickname = null;
        String displayName = null;

        if (userDetails != null && userDetails.getUser() != null) {
            nickname = userDetails.getUser().getNickname();
            loginUserId = userDetails.getUser().getId();
            // 닉네임이 없으면 이메일로 대체
            String email = userDetails.getUser().getEmail();
            displayName = (nickname != null && !nickname.isBlank())
                    ? nickname
                    : (email != null ? email : "");
        } else {
            displayName = "";
        }

        model.addAttribute("nickname", nickname);
        model.addAttribute("loginUserId", loginUserId);
        model.addAttribute("displayName", displayName);  // ★ 요거 추가

        List<Feed> latestFeeds = feedService.getLatestFeeds(3);
        if (latestFeeds != null) {
            for (Feed f : latestFeeds) {
                f.setLikeCount(feedService.getLikeCount(f.getId()));
                f.setCommentCount(feedService.getCommentCount(f.getId()));
            }
        }
        model.addAttribute("feeds", latestFeeds != null ? latestFeeds : List.of());

        return "user/home";
    }

}
