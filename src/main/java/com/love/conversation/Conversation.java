package com.love.conversation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_user_id", columnList = "user_id")
})
public class Conversation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 120)
    private String title;

    @Column(name = "scenario_key", length = 50)
    private String scenarioKey;

    @Column(name = "persona_key", length = 50)
    private String personaKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 20)
    private ConversationMode mode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastMessageAt = now;
        if (this.mode == null) {
            this.mode = ConversationMode.SIMULATION;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // getters/setters
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getScenarioKey() { return scenarioKey; }
    public void setScenarioKey(String scenarioKey) { this.scenarioKey = scenarioKey; }
    public String getPersonaKey() { return personaKey; }
    public void setPersonaKey(String personaKey) { this.personaKey = personaKey; }
    public ConversationMode getMode() { return mode; }
    public void setMode(ConversationMode mode) { this.mode = mode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}