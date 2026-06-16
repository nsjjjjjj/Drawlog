package com.drawlog.topic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class TopicDtos {
    public record TodayTopicResponse(Long id, String text, LocalDate date, boolean fromSuggestion) {}
    public record SuggestionRequest(Long groupId, @NotBlank @Size(min = 1, max = 120) String text) {}
    public record SuggestionResponse(Long id, String text, LocalDate targetDate) {}
}
