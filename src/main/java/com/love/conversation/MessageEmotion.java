package com.love.conversation;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Python AI 서버에서 반환한 감정 분석 결과 저장 (Java는 DB 저장만, 분석 로직 없음)
 */
@Entity
@Table(name = "message_emotions", indexes = {
        @Index(name = "idx_message_emotions_conversation_id", columnList = "conversation_id"),
        @Index(name = "idx_message_emotions_message_id", columnList = "message_id")
})
public class MessageEmotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false, length = 30)
    private String emotion;

    @Column(nullable = false)
    private Double confidence;

    @Column(name = "scores_json", columnDefinition = "text")
    private String scoresJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getEmotion() { return emotion; }
    public void setEmotion(String emotion) { this.emotion = emotion; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getScoresJson() { return scoresJson; }
    public void setScoresJson(String scoresJson) { this.scoresJson = scoresJson; }
    public Instant getCreatedAt() { return createdAt; }
}
