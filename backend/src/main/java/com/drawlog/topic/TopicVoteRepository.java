package com.drawlog.topic;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicVoteRepository extends JpaRepository<TopicVote, Long> {
    interface SuggestionVoteCount {
        Long getSuggestionId();
        long getVoteCount();
    }

    Optional<TopicVote> findByGroupIdAndUserIdAndTargetDate(Long groupId, Long userId, LocalDate targetDate);
    long countBySuggestionId(Long suggestionId);
    List<TopicVote> findByGroupIdAndTargetDate(Long groupId, LocalDate targetDate);
    void deleteByGroupId(Long groupId);

    @Query("""
            select vote.suggestion.id as suggestionId, count(vote.id) as voteCount
            from TopicVote vote
            where vote.group.id = :groupId
              and vote.targetDate = :targetDate
            group by vote.suggestion.id
            """)
    List<SuggestionVoteCount> countBySuggestionForDate(@Param("groupId") Long groupId, @Param("targetDate") LocalDate targetDate);
}
