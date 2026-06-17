package com.drawlog.topic;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyTopicRepository extends JpaRepository<DailyTopic, Long> {
    Optional<DailyTopic> findByGroupIdAndTopicDate(Long groupId, LocalDate topicDate);

    @Modifying
    @Query("update DailyTopic topic set topic.selectedSuggestion = null where topic.group.id = :groupId")
    void clearSelectedSuggestionsForGroup(@Param("groupId") Long groupId);

    void deleteByGroupId(Long groupId);
}
