package com.love.user;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 여친 유형 등 테스트 결과 저장. JPA로 테이블만 생성(ddl-auto)하고,
 * 조회/저장은 기존대로 JdbcTemplate 사용해도 됨.
 */
@Entity
@Table(name = "test_results", indexes = {
        @Index(name = "idx_test_results_user_test", columnList = "user_id, test_key")
})
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "test_key", nullable = false, length = 50)
    private String testKey;

    @Column(name = "result_value", nullable = false, length = 500)
    private String resultValue;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTestKey() { return testKey; }
    public void setTestKey(String testKey) { this.testKey = testKey; }
    public String getResultValue() { return resultValue; }
    public void setResultValue(String resultValue) { this.resultValue = resultValue; }
    public Instant getCreatedAt() { return createdAt; }
}
