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
            Long drawingId
    ) {}

    public record QuoteResponse(
            Long drawingId,
            String thumbnailUrl,
            String username,
            String topicText
    ) {}

    public record ChatMessageResponse(
            Long id,
            Long groupId,
            Long userId,
            String username,
            ChatMessageType type,
            String content,
            Long drawingId,
            Instant deletedAt,
            Instant createdAt,
            QuoteResponse quote
    ) {}

    public record ChatPageResponse(List<ChatMessageResponse> messages, Long nextCursor) {}
}
