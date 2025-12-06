package com.stylemate.repository;

import com.stylemate.model.Avatar;
import com.stylemate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AvatarRepository extends JpaRepository<Avatar, Long> {
    Optional<Avatar> findByUser(User user);
    boolean existsByUser(User user);
}
