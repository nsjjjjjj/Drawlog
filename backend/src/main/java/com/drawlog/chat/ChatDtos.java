package com.drawlog.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public class ChatDtos {
    public record SendMessageRequest(
            @NotNull ChatMessageType type,
            @NotBlank @Size(max = 1000) String content,
            Long drawingId,
            Long replyToMessageId
    ) {}

    public record QuoteResponse(
            Long drawingId,
            String imageUrl,
            String imagePath,
            String thumbnailUrl,
            String username,
            String topicText
    ) {}

    public record ChatMessageResponse(
            Long id,
            Long groupId,
            Long userId,
            String username,
            String profileImageUrl,
            ChatMessageType type,
            String content,
            Long drawingId,
            Instant deletedAt,
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

    public record ChatPageResponse(List<ChatMessageResponse> messages, Long nextCursor) {}
}
