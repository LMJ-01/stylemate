package com.stylemate.controller;

import com.stylemate.config.security.UserDetailsImpl;
import com.stylemate.model.User;
import com.stylemate.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "user/register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user, Model model) {
        if (userService.isEmailDuplicate(user.getEmail())) {
            model.addAttribute("error", "이미 사용 중인 이메일입니다.");
            return "user/register";
        }
        if (userService.isNicknameDuplicate(user.getNickname())) {
            model.addAttribute("error", "이미 사용 중인 닉네임입니다.");
            return "user/register";
        }
        userService.save(user);
        return "redirect:/user/login";
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(value = "error", required = false) String error,
                                @RequestParam(value = "logout", required = false) String logout,
                                Model model) {
        if (error != null) {
            model.addAttribute("error", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        if (logout != null) {
            model.addAttribute("message", "성공적으로 로그아웃되었습니다.");
        }
        return "user/login";
    }

    @GetMapping("/profile/{userId}")
    public String viewProfile(@PathVariable Long userId,
                              @AuthenticationPrincipal UserDetailsImpl userDetails,
                              Model model) {
        User user = userService.findById(userId);
        model.addAttribute("profileUser", user);
        model.addAttribute("loginUserId", userDetails.getUser().getId());
        return "user/profile";
    }

    @GetMapping("/edit")
    public String editProfileForm(Model model,
                                  @AuthenticationPrincipal UserDetailsImpl userDetails) {
        model.addAttribute("user", userDetails.getUser());
        return "user/edit";
    }

    @PostMapping("/edit")
    public String updateProfile(@ModelAttribute User userForm,
                                @RequestParam("imageFile") MultipartFile imageFile,
                                @AuthenticationPrincipal UserDetailsImpl userDetails,
                                Model model) {

        try {
            String imageUrl = userDetails.getUser().getProfileImage(); // 기존 이미지 유지

            // 이미지가 업로드된 경우
            if (imageFile != null && !imageFile.isEmpty()) {
                String uploadDir = System.getProperty("user.home") + "/stylemate-uploads/";
                File dir = new File(uploadDir);
                if (!dir.exists()) dir.mkdirs();

                String fileName = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();
                File dest = new File(uploadDir + fileName);
                imageFile.transferTo(dest);

                imageUrl = "/uploads/" + fileName;
            }

            userService.updateUserProfile(
                    userDetails.getUser().getId(),
                    userForm.getBio(),
                    imageUrl
            );

            return "redirect:/user/profile/" + userDetails.getUser().getId();

        } catch (IOException e) {
            e.printStackTrace();
            model.addAttribute("error", "프로필 수정 중 오류 발생: " + e.getMessage());
            return "user/edit";
        }
    }
}
