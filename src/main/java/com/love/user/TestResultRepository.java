package com.love.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    boolean existsByUserIdAndTestKey(Long userId, String testKey);

    Optional<TestResult> findFirstByUserIdAndTestKeyOrderByIdDesc(Long userId, String testKey);
}
