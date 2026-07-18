package com.gisagent.repository;

import com.gisagent.entity.KbSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KbSyncStateRepository extends JpaRepository<KbSyncState, Long> {
    Optional<KbSyncState> findByUserIdAndKbId(Long userId, String kbId);

    List<KbSyncState> findByUserId(Long userId);
}
