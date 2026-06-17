package com.drawlog.feed;

import com.drawlog.drawing.DrawingDtos;
import com.drawlog.group.MemberRole;
import com.drawlog.topic.TopicDtos;
import com.drawlog.user.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class FeedDtos {
    public record MemberDrawingResponse(
            Long userId,
            String nickname,
            String profileImageUrl,
            UserStatus status,
            MemberRole role,
            Instant joinedAt,
            boolean submitted,
            DrawingDtos.DrawingResponse drawing
    ) {}

    public record FeedResponse(
            LocalDate date,
            TopicDtos.DailyTopicResponse dailyTopic,
            boolean submitted,
            boolean feedLocked,
            List<MemberDrawingResponse> members
    ) {}

    public record FeedDatesResponse(List<LocalDate> dates) {}
}
