package com.stylemate.repository;

import com.stylemate.model.FittingRoomSet;
import com.stylemate.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FittingRoomSetRepository extends JpaRepository<FittingRoomSet, Long> {
    List<FittingRoomSet> findByUserOrderByCreatedAtDesc(User user);
}
