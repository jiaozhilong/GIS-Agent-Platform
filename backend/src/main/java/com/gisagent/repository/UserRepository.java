package com.gisagent.repository;

import com.gisagent.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);

    // P8-1 计费纵深：配额告警通知范围（组织管理员 + 平台超管）
    List<User> findByOrganizationIdAndRole(Long organizationId, String role);
    List<User> findByRole(String role);
}
