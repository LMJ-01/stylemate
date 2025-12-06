package com.stylemate.service;

import com.stylemate.model.FittingRoomSet;
import com.stylemate.model.User;
import com.stylemate.repository.FittingRoomSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FittingRoomSetService {

    private final FittingRoomSetRepository repo;

    /** ✅ 피팅 세트 저장 */
    public FittingRoomSet save(FittingRoomSet set) {
        return repo.save(set);
    }

    /** ✅ 특정 유저의 세트 목록 (최신순) */
    public List<FittingRoomSet> getUserSets(User user) {
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    /** ✅ 세트 단일 조회 */
    public FittingRoomSet getById(Long id) {
        return repo.findById(id).orElse(null);
    }

    /** ✅ 세트 삭제 (본인만 가능) */
    public boolean deleteById(Long id, User user) {
        FittingRoomSet set = repo.findById(id).orElse(null);
        if (set == null) return false;

        // 본인 소유가 아닌 경우 삭제 불가
        if (!set.getUser().getId().equals(user.getId())) return false;

        repo.delete(set);
        return true;
    }
}
