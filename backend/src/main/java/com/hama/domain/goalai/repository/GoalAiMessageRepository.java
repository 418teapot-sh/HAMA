package com.hama.domain.goalai.repository;

import com.hama.domain.goalai.entity.GoalAiMessage;
import com.hama.domain.goalai.entity.MessageRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalAiMessageRepository extends JpaRepository<GoalAiMessage, Long> {

    List<GoalAiMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    long countBySessionIdAndRole(Long sessionId, MessageRole role);
}
