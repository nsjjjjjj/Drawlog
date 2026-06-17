package com.drawlog.topic;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicSuggestionRepository extends JpaRepository<TopicSuggestion, Long> {
    List<TopicSuggestion> findByGroupIdAndTargetDateOrderByCreatedAtAsc(Long groupId, LocalDate targetDate);
    Optional<TopicSuggestion> findByGroupIdAndUserIdAndTargetDate(Long groupId, Long userId, LocalDate targetDate);
    void deleteByGroupId(Long groupId);
}
