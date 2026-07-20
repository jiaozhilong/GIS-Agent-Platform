package com.gisagent.repository;

import com.gisagent.entity.UsageQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsageQuotaRepository extends JpaRepository<UsageQuota, Long> {
    Optional<UsageQuota> findByOrganizationId(Long organizationId);
    boolean existsByOrganizationId(Long organizationId);
}
