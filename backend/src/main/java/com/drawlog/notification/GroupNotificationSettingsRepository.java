package com.drawlog.notification;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupNotificationSettingsRepository extends JpaRepository<GroupNotificationSettings, Long> {
    Optional<GroupNotificationSettings> findByUserIdAndGroupId(Long userId, Long groupId);
}
