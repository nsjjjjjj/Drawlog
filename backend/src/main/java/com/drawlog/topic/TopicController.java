package com.drawlog.topic;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicController {
    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping("/today")
    public TopicDtos.TodayTopicResponse today(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long groupId) {
        DailyTopic topic = topicService.today(user.id(), groupId);
        return new TopicDtos.TodayTopicResponse(topic.getId(), topic.getText(), topic.getDate(), topic.isFromSuggestion());
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDtos.SuggestionResponse suggest(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TopicDtos.SuggestionRequest request) {
        TopicSuggestion suggestion = topicService.suggest(user.id(), request.groupId(), request.text());
        return new TopicDtos.SuggestionResponse(suggestion.getId(), suggestion.getText(), suggestion.getTargetDate());
    }

    @GetMapping("/suggestions/mine")
    public TopicDtos.SuggestionResponse mine(@AuthenticationPrincipal CurrentUser user, @RequestParam(required = false) Long groupId) {
        return topicService.mySuggestion(user.id(), groupId)
                .map(suggestion -> new TopicDtos.SuggestionResponse(suggestion.getId(), suggestion.getText(), suggestion.getTargetDate()))
                .orElse(null);
    }

    @PutMapping("/suggestions/{id}")
    public TopicDtos.SuggestionResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id, @Valid @RequestBody TopicDtos.SuggestionRequest request) {
        TopicSuggestion suggestion = topicService.updateSuggestion(user.id(), id, request.text());
        return new TopicDtos.SuggestionResponse(suggestion.getId(), suggestion.getText(), suggestion.getTargetDate());
    }

    @DeleteMapping("/suggestions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        topicService.deleteSuggestion(user.id(), id);
    }
}
