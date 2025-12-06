package com.stylemate.service;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.stylemate.model.User;
import com.stylemate.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User save(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean isEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    public boolean isNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    public User findById(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
    }

    /**
     * 프로필 정보 업데이트
     * - bio: 자기소개
     * - profileImage: 이미지 경로 (null이면 기존 이미지 유지)
     */
    public void updateUserProfile(Long userId, String bio, String profileImage) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));

        user.setBio(bio);

        if (profileImage != null && !profileImage.isBlank()) {
            user.setProfileImage(profileImage);
        }

        userRepository.save(user);
    }
}
