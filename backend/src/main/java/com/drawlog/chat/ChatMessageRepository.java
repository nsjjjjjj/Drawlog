package com.drawlog.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop100ByGroupIdOrderByCreatedAtAsc(Long groupId);
    void deleteByGroupId(Long groupId);
}
