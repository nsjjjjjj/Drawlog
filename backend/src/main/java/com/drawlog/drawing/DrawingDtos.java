package com.drawlog.drawing;

import java.time.Instant;

public class DrawingDtos {
    public record DrawingResponse(
            Long id,
            String thumbnailUrl,
            String strokeData,
            Long userId,
            String nickname,
            String profileImageUrl,
            Long groupId,
            Long dailyTopicId,
            String topicText,
            Instant submittedAt,
            Instant updatedAt,
            Instant lockedAt
    ) {}

    public record MyDrawingResponse(DrawingResponse drawing) {}
}
