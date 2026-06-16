package com.drawlog.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByGroupIdAndDeletedAtIsNullAndIdLessThanOrderByCreatedAtDesc(Long groupId, Long cursor, Pageable pageable);
    List<ChatMessage> findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long groupId, Pageable pageable);
}
