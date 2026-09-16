package com.commengine.repository;

import com.commengine.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Optional<Conversation> findByUserOneIdAndUserTwoId(String userOneId, String userTwoId);

    @Query("select c from Conversation c where c.userOne.id = :userId or c.userTwo.id = :userId order by c.updatedAt desc")
    List<Conversation> findAllForUser(@Param("userId") String userId);
}
