package com.gisagent.repository;

import com.gisagent.entity.ImaKbConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ImaKbConfigRepository extends JpaRepository<ImaKbConfig, Long> {
    List<ImaKbConfig> findByUserId(Long userId);
    List<ImaKbConfig> findByUserIdAndEnabledTrue(Long userId);
    List<ImaKbConfig> findByUserIdAndPurpose(Long userId, String purpose);
    Optional<ImaKbConfig> findByUserIdAndKbId(Long userId, String kbId);
}
