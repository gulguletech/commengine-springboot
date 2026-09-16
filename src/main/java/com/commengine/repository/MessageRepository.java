package com.commengine.repository;

import com.commengine.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, String> {

    List<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

    List<Message> findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(String conversationId, Instant before, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);

    @Modifying
    @Query("update Message m set m.isRead = true where m.conversation.id = :conversationId and m.sender.id <> :userId and m.isRead = false")
    int markConversationRead(@Param("conversationId") String conversationId, @Param("userId") String userId);
}
