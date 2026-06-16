package com.drawlog.notification;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/notifications")
    public java.util.List<NotificationDtos.NotificationResponse> notifications(@AuthenticationPrincipal CurrentUser user) {
        return notificationService.notifications(user.id());
    }

    @PatchMapping("/api/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal CurrentUser user) {
        notificationService.readAll(user.id());
    }

    @GetMapping("/api/notification-settings")
    public NotificationDtos.SettingsResponse settings(@AuthenticationPrincipal CurrentUser user) {
        return notificationService.settings(user.id());
    }

    @PatchMapping("/api/notification-settings")
    public NotificationDtos.SettingsResponse updateSettings(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody NotificationDtos.UpdateUserSettingsRequest request
    ) {
        return notificationService.updateUserSettings(user.id(), request.allEnabled());
    }

    @PatchMapping("/api/groups/{groupId}/notification-setting")
    public NotificationDtos.GroupSettingResponse updateGroupSettings(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId,
            @Valid @RequestBody NotificationDtos.UpdateGroupSettingsRequest request
    ) {
        return notificationService.updateGroupSettings(user.id(), groupId, request.enabled());
    }
}
