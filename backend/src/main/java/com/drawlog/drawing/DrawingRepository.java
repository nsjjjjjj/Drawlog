package com.drawlog.drawing;

import java.util.List;
import java.util.Optional;
import com.drawlog.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrawingRepository extends JpaRepository<Drawing, Long> {
    Optional<Drawing> findByGroupIdAndDailyTopicIdAndUserId(Long groupId, Long dailyTopicId, Long userId);
    boolean existsByGroupIdAndDailyTopicTopicDateAndUserId(Long groupId, java.time.LocalDate topicDate, Long userId);
    List<Drawing> findByGroupIdAndDailyTopicId(Long groupId, Long dailyTopicId);
    List<Drawing> findByGroupId(Long groupId);
    List<Drawing> findByUserId(Long userId);
    List<Drawing> findByUserStatus(UserStatus status);
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

    @Query("""
            select distinct drawing.dailyTopic.topicDate
            from Drawing drawing
            where drawing.group.id = :groupId
              and drawing.dailyTopic.topicDate <= :today
              and drawing.user.status = :status
            order by drawing.dailyTopic.topicDate asc
            """)
    List<java.time.LocalDate> findRecordDatesByUserStatus(@Param("groupId") Long groupId,
                                                          @Param("today") java.time.LocalDate today,
                                                          @Param("status") UserStatus status);
}
