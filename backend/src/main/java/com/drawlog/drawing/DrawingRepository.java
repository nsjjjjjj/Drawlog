package com.drawlog.drawing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawingRepository extends JpaRepository<Drawing, Long> {
    Optional<Drawing> findByGroupIdAndDailyTopicIdAndUserId(Long groupId, Long dailyTopicId, Long userId);
    boolean existsByGroupIdAndDailyTopicTopicDateAndUserId(Long groupId, java.time.LocalDate topicDate, Long userId);
    List<Drawing> findByGroupIdAndDailyTopicId(Long groupId, Long dailyTopicId);
    List<Drawing> findByDailyTopicTopicDateBeforeAndLockedAtIsNull(java.time.LocalDate date);
    boolean existsByGroupIdAndDailyTopicIdAndUserId(Long groupId, Long dailyTopicId, Long userId);
}
