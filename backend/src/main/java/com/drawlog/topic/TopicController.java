package com.drawlog.topic;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/topics")
public class TopicController {
    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping("/today")
    public TopicDtos.DailyTopicResponse today(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId) {
        return topicService.toResponse(topicService.ensureDailyTopic(user.id(), groupId, topicService.today()));
    }

    @GetMapping("/suggestions")
    public List<TopicDtos.SuggestionResponse> suggestions(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate
    ) {
        return topicService.suggestions(user.id(), groupId, targetDate);
    }

    @PostMapping("/suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDtos.SuggestionResponse suggest(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @Valid @RequestBody TopicDtos.SuggestionRequest request) {
        LocalDate targetDate = request.targetDate() == null ? topicService.today().plusDays(1) : request.targetDate();
        return topicService.suggest(user.id(), groupId, targetDate, request.text());
    }

    @PatchMapping("/suggestions/{suggestionId}")
    public TopicDtos.SuggestionResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @PathVariable Long suggestionId, @Valid @RequestBody TopicDtos.SuggestionRequest request) {
        return topicService.update(user.id(), groupId, suggestionId, request.text());
    }

    @DeleteMapping("/suggestions/{suggestionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @PathVariable Long suggestionId) {
        topicService.delete(user.id(), groupId, suggestionId);
    }

    @PostMapping("/suggestions/{suggestionId}/vote")
    public TopicDtos.MyVoteResponse vote(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @PathVariable Long suggestionId) {
        return topicService.vote(user.id(), groupId, suggestionId);
    }

    @GetMapping("/my-vote")
    public TopicDtos.MyVoteResponse myVote(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate
    ) {
        return topicService.myVote(user.id(), groupId, targetDate);
    }
}
