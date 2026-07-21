package com.gisagent.repository;

import com.gisagent.entity.ImaCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImaCredentialRepository extends JpaRepository<ImaCredential, Long> {
    Optional<ImaCredential> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
