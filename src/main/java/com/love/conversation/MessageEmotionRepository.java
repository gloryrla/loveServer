package com.love.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageEmotionRepository extends JpaRepository<MessageEmotion, Long> {
    List<MessageEmotion> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
