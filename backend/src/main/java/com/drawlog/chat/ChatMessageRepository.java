package com.drawlog.chat;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByGroupIdAndDeletedAtIsNullAndIdLessThanOrderByCreatedAtDesc(Long groupId, Long cursor, Pageable pageable);
    List<ChatMessage> findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long groupId, Pageable pageable);
    List<ChatMessage> findByDrawingIdIn(List<Long> drawingIds);
    List<ChatMessage> findBySenderIdAndDeletedAtIsNull(Long senderId);

    @Modifying
    @Query("update ChatMessage message set message.drawing = null where message.drawing.id in :drawingIds")
    void clearDrawingReferences(@Param("drawingIds") List<Long> drawingIds);

    @Modifying
    @Query("update ChatMessage message set message.replyToMessage = null where message.group.id = :groupId")
    void clearReplyReferencesForGroup(@Param("groupId") Long groupId);

    @Modifying
    @Query("update ChatMessage message set message.replyToMessage = null where message.replyToMessage.sender.id = :senderId")
    void clearReplyReferencesToSenderMessages(@Param("senderId") Long senderId);

    void deleteByGroupId(Long groupId);
}
