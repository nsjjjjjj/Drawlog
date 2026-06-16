package com.drawlog.drawing;

import java.time.Instant;
import java.time.LocalDate;

public class DrawingDtos {
    public record DrawingResponse(
            Long id,
            String imageUrl,
            Long userId,
            String username,
            Long groupId,
            Long topicId,
            String topicText,
            Instant createdAt
    ) {}

    public record MemberDrawingResponse(Long userId, String username, boolean owner, DrawingResponse drawing) {}
    public record FeedResponse(LocalDate date, Long topicId, String topicText, java.util.List<DrawingResponse> drawings, java.util.List<MemberDrawingResponse> members) {}
}
