package com.drawlog.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public class ChatDtos {
    public record SendMessageRequest(
            @NotBlank @Size(max = 1000) String content,
            Long drawingId,
            Long replyToMessageId
    ) {}

    public record QuoteResponse(
            Long drawingId,
            String imageUrl,
            String username,
            String topicText
    ) {}

    public record ReplyResponse(
            Long messageId,
            String username,
            String content
    ) {}

    public record ChatMessageResponse(
            Long id,
            Long groupId,
            Long userId,
            String username,
            String profileImageUrl,
            ChatMessageType type,
            String content,
            Instant createdAt,
            QuoteResponse quote,
            ReplyToMessageResponse replyTo
    ) {}

    public record ReplyToMessageResponse(
            Long id,
            Long userId,
            String username,
            String content,
            Instant createdAt
    ) {}
}
