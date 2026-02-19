package com.love.conversation;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Python AI 서버에서 반환한 리포트 저장 (Java는 DB 저장만, 생성 로직 없음)
 */
@Entity
@Table(name = "conversation_reports", indexes = {
        @Index(name = "idx_conversation_reports_conversation_id", columnList = "conversation_id")
})
public class ConversationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "details_json", columnDefinition = "text")
    private String detailsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
