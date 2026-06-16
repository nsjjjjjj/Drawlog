package com.drawlog.topic;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTopicRepository extends JpaRepository<DailyTopic, Long> {
    Optional<DailyTopic> findByGroupIdAndDate(Long groupId, LocalDate date);
    void deleteByGroupId(Long groupId);
}
