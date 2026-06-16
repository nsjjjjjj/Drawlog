package com.drawlog.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class TopicDtos {
    public record DailyTopicResponse(Long id, Long groupId, LocalDate date, String text, Long selectedSuggestionId) {}
    public record SuggestionRequest(@NotBlank @Size(max = 120) String text, LocalDate targetDate) {}
    public record SuggestionResponse(Long id, Long userId, String nickname, Long groupId, LocalDate targetDate, String text, long voteCount, boolean mine, boolean editable) {}
    public record MyVoteResponse(Long suggestionId) {}
}
