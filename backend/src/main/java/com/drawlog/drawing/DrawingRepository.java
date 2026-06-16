package com.drawlog.drawing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DrawingRepository extends JpaRepository<Drawing, Long> {
    List<Drawing> findByGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long groupId, Instant start, Instant end);
    Optional<Drawing> findByGroupIdAndTopicIdAndUserId(Long groupId, Long topicId, Long userId);
    List<Drawing> findByGroupId(Long groupId);
}
