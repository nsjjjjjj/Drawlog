package com.drawlog.notification;

import java.time.Instant;
import java.util.List;

public class NotificationDtos {
    public record NotificationResponse(
            Long id,
            Long groupId,
            String type,
            String title,
            String message,
            String targetType,
            Long targetId,
            Instant readAt,
            Instant createdAt
    ) {}

    public record UnreadCountResponse(long count) {}

    public record SettingsResponse(boolean allEnabled, List<GroupSettingResponse> groups) {}

    public record GroupSettingResponse(Long groupId, String groupName, boolean enabled) {}

    public record UpdateUserSettingsRequest(boolean allEnabled) {}

    public record UpdateGroupSettingsRequest(boolean enabled) {}
}
