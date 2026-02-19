package com.love.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationReportRepository extends JpaRepository<ConversationReport, Long> {
    List<ConversationReport> findByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
