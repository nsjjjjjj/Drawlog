package com.drawlog.drawing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrawingRepository extends JpaRepository<Drawing, Long> {
    Optional<Drawing> findByGroupIdAndDailyTopicIdAndUserId(Long groupId, Long dailyTopicId, Long userId);
    boolean existsByGroupIdAndDailyTopicTopicDateAndUserId(Long groupId, java.time.LocalDate topicDate, Long userId);
    List<Drawing> findByGroupIdAndDailyTopicId(Long groupId, Long dailyTopicId);
    List<Drawing> findByDailyTopicTopicDateBeforeAndLockedAtIsNull(java.time.LocalDate date);
    boolean existsByGroupIdAndDailyTopicIdAndUserId(Long groupId, Long dailyTopicId, Long userId);

    @Query("""
            select distinct drawing.dailyTopic.topicDate
            from Drawing drawing
            where drawing.group.id = :groupId
              and drawing.dailyTopic.topicDate <= :today
            order by drawing.dailyTopic.topicDate asc
            """)
    List<java.time.LocalDate> findRecordDates(@Param("groupId") Long groupId, @Param("today") java.time.LocalDate today);
}
