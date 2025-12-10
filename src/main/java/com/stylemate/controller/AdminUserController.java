package com.stylemate.controller;

import com.stylemate.model.User;
import com.stylemate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;

    @GetMapping
    public String usersPage() {
        return "admin/users";
    }

    @GetMapping("/api")
    @ResponseBody
    public List<UserSummary> list(@RequestParam(value = "q", required = false) String q) {
        List<User> all = userRepository.findAll();
        String keyword = q != null ? q.trim().toLowerCase() : "";
        return all.stream()
                .filter(u -> keyword.isEmpty()
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(keyword))
                        || (u.getNickname() != null && u.getNickname().toLowerCase().contains(keyword)))
                .map(UserSummary::from)
                .collect(Collectors.toList());
    }

    @PatchMapping("/api/{id}/role")
    @ResponseBody
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody RoleChangeRequest req) {
        return userRepository.findById(id)
                .map(u -> {
                    u.setRole(req.getRole());
                    userRepository.save(u);
                    return ResponseEntity.ok().body(UserSummary.from(u));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public static class RoleChangeRequest {
        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public static class UserSummary {
        public Long id;
        public String email;
        public String nickname;
        public String role;
        public String profileImage;
        public String bio;

        public static UserSummary from(User u) {
            UserSummary s = new UserSummary();
            s.id = u.getId();
            s.email = u.getEmail();
            s.nickname = u.getNickname();
            s.role = u.getRole();
            s.profileImage = u.getProfileImage();
            s.bio = u.getBio();
            return s;
        }
    }
}
