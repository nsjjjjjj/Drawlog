package com.drawlog.topic;

import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.config.AppProperties;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.FriendGroupRepository;
import com.drawlog.group.GroupService;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicService {
    private static final List<String> DEFAULT_TOPICS = List.of("작은 행성 위의 집", "비 오는 날의 친구", "가장 좋아하는 간식", "미래의 내 방", "이상한 동물");
    private final SecureRandom random = new SecureRandom();
    private final GroupService groupService;
    private final FriendGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TopicSuggestionRepository suggestionRepository;
    private final TopicVoteRepository voteRepository;
    private final DailyTopicRepository dailyTopicRepository;
    private final AppProperties properties;

    public TopicService(GroupService groupService, FriendGroupRepository groupRepository, UserRepository userRepository,
                        TopicSuggestionRepository suggestionRepository, TopicVoteRepository voteRepository,
                        DailyTopicRepository dailyTopicRepository, AppProperties properties) {
        this.groupService = groupService;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.suggestionRepository = suggestionRepository;
        this.voteRepository = voteRepository;
        this.dailyTopicRepository = dailyTopicRepository;
        this.properties = properties;
    }

    @Transactional
    public DailyTopic ensureDailyTopic(Long userId, Long groupId, LocalDate date) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        return dailyTopicRepository.findByGroupIdAndTopicDate(groupId, date).orElseGet(() -> selectDailyTopic(group, date));
    }

    @Transactional
    public DailyTopic selectDailyTopic(FriendGroup group, LocalDate date) {
        List<TopicSuggestion> suggestions = suggestionRepository.findByGroupIdAndTargetDateOrderByCreatedAtAsc(group.getId(), date);
        TopicSuggestion selected = null;
        String text;
        if (suggestions.isEmpty()) {
            text = DEFAULT_TOPICS.get(random.nextInt(DEFAULT_TOPICS.size()));
        } else {
            Map<Long, Long> counts = voteRepository.findByGroupIdAndTargetDate(group.getId(), date).stream()
                    .collect(Collectors.groupingBy(vote -> vote.getSuggestion().getId(), Collectors.counting()));
            long max = suggestions.stream().mapToLong(suggestion -> counts.getOrDefault(suggestion.getId(), 0L)).max().orElse(0L);
            List<TopicSuggestion> candidates = suggestions.stream()
                    .filter(suggestion -> counts.getOrDefault(suggestion.getId(), 0L) == max)
                    .toList();
            selected = candidates.get(random.nextInt(candidates.size()));
            text = selected.getText();
        }
        DailyTopic topic = new DailyTopic();
        topic.setGroup(group);
        topic.setTopicDate(date);
        topic.setText(text);
        topic.setSelectedSuggestion(selected);
        return dailyTopicRepository.save(topic);
    }

    @Transactional
    public List<TopicDtos.SuggestionResponse> suggestions(Long userId, Long groupId, LocalDate targetDate) {
        groupService.requireGroup(userId, groupId);
        Map<Long, Long> voteCounts = voteRepository.countBySuggestionForDate(groupId, targetDate).stream()
                .collect(Collectors.toMap(TopicVoteRepository.SuggestionVoteCount::getSuggestionId, TopicVoteRepository.SuggestionVoteCount::getVoteCount));
        return suggestionRepository.findByGroupIdAndTargetDateOrderByCreatedAtAsc(groupId, targetDate).stream()
                .map(suggestion -> toResponse(userId, suggestion, voteCounts.getOrDefault(suggestion.getId(), 0L)))
                .toList();
    }

    @Transactional
    public TopicDtos.SuggestionResponse suggest(Long userId, Long groupId, LocalDate targetDate, String text) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        if (suggestionRepository.findByGroupIdAndUserIdAndTargetDate(groupId, userId, targetDate).isPresent()) {
            throw new ApiException(ErrorCode.TOPIC_ALREADY_EXISTS);
        }
        TopicSuggestion suggestion = new TopicSuggestion();
        suggestion.setGroup(group);
        suggestion.setUser(user(userId));
        suggestion.setTargetDate(targetDate);
        suggestion.setText(text.trim());
        return toResponse(userId, suggestionRepository.save(suggestion), 0L);
    }

    @Transactional
    public TopicDtos.SuggestionResponse update(Long userId, Long groupId, Long suggestionId, String text) {
        groupService.requireGroup(userId, groupId);
        TopicSuggestion suggestion = ownSuggestion(userId, groupId, suggestionId);
        ensureEditable(suggestion);
        suggestion.setText(text.trim());
        return toResponse(userId, suggestion, voteRepository.countBySuggestionId(suggestion.getId()));
    }

    @Transactional
    public void delete(Long userId, Long groupId, Long suggestionId) {
        groupService.requireGroup(userId, groupId);
        TopicSuggestion suggestion = ownSuggestion(userId, groupId, suggestionId);
        ensureEditable(suggestion);
        suggestionRepository.delete(suggestion);
    }

    @Transactional
    public TopicDtos.MyVoteResponse vote(Long userId, Long groupId, Long suggestionId) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        TopicSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .filter(found -> found.getGroup().getId().equals(groupId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "주제 후보를 찾을 수 없습니다."));
        TopicVote vote = voteRepository.findByGroupIdAndUserIdAndTargetDate(groupId, userId, suggestion.getTargetDate())
                .orElseGet(TopicVote::new);
        if (vote.getId() == null) {
            vote.setGroup(group);
            vote.setUser(user(userId));
            vote.setTargetDate(suggestion.getTargetDate());
        }
        vote.setSuggestion(suggestion);
        voteRepository.save(vote);
        return new TopicDtos.MyVoteResponse(suggestion.getId());
    }

    @Transactional
    public TopicDtos.MyVoteResponse myVote(Long userId, Long groupId, LocalDate targetDate) {
        groupService.requireGroup(userId, groupId);
        return new TopicDtos.MyVoteResponse(voteRepository.findByGroupIdAndUserIdAndTargetDate(groupId, userId, targetDate)
                .map(vote -> vote.getSuggestion().getId())
                .orElse(null));
    }

    @Transactional
    public void ensureTodayTopicsForAllGroups(LocalDate date) {
        groupRepository.findAll().forEach(group -> dailyTopicRepository.findByGroupIdAndTopicDate(group.getId(), date)
                .orElseGet(() -> selectDailyTopic(group, date)));
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of(properties.getTimeZone()));
    }

    public TopicDtos.DailyTopicResponse toResponse(DailyTopic topic) {
        return new TopicDtos.DailyTopicResponse(topic.getId(), topic.getGroup().getId(), topic.getTopicDate(), topic.getText(),
                topic.getSelectedSuggestion() == null ? null : topic.getSelectedSuggestion().getId());
    }

    private TopicDtos.SuggestionResponse toResponse(Long userId, TopicSuggestion suggestion, long voteCount) {
        boolean mine = suggestion.getUser().getId().equals(userId);
        return new TopicDtos.SuggestionResponse(
                suggestion.getId(),
                suggestion.getUser().getId(),
                suggestion.getUser().getNickname(),
                suggestion.getGroup().getId(),
                suggestion.getTargetDate(),
                suggestion.getText(),
                voteCount,
                mine,
                mine && voteCount == 0
        );
    }

    private TopicSuggestion ownSuggestion(Long userId, Long groupId, Long suggestionId) {
        return suggestionRepository.findById(suggestionId)
                .filter(suggestion -> suggestion.getGroup().getId().equals(groupId) && suggestion.getUser().getId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "주제 후보를 찾을 수 없습니다."));
    }

    private void ensureEditable(TopicSuggestion suggestion) {
        if (voteRepository.countBySuggestionId(suggestion.getId()) > 0) throw new ApiException(ErrorCode.TOPIC_LOCKED_BY_VOTE);
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
