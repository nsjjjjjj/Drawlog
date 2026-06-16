package com.drawlog.topic;

import com.drawlog.config.AppProperties;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.FriendGroupRepository;
import com.drawlog.group.GroupService;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TopicService {
    private static final List<String> DEFAULT_TOPICS = List.of(
            "오늘 가장 기억에 남은 장면",
            "작은 행성 위의 집",
            "비 오는 날의 친구",
            "내 책상 위 비밀 물건",
            "상상 속 간식",
            "초록색으로 시작하는 이야기",
            "잠든 도시",
            "미래의 나에게 보내는 그림",
            "구름으로 만든 동물",
            "하루를 저장하는 병"
    );

    private final Random random = new Random();
    private final DailyTopicRepository dailyTopicRepository;
    private final TopicSuggestionRepository suggestionRepository;
    private final FriendGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final AppProperties properties;

    public TopicService(DailyTopicRepository dailyTopicRepository, TopicSuggestionRepository suggestionRepository,
                        FriendGroupRepository groupRepository, UserRepository userRepository,
                        GroupService groupService, AppProperties properties) {
        this.dailyTopicRepository = dailyTopicRepository;
        this.suggestionRepository = suggestionRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.properties = properties;
    }

    @Transactional
    public DailyTopic today(Long userId, Long groupId) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        return ensureDailyTopic(group, LocalDate.now(zone()));
    }

    @Transactional
    public TopicSuggestion suggest(Long userId, Long groupId, String text) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        LocalDate targetDate = LocalDate.now(zone()).plusDays(1);
        suggestionRepository.findByGroupIdAndUserIdAndTargetDate(group.getId(), userId, targetDate)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "내일 주제 제안은 한 번만 할 수 있습니다.");
                });
        TopicSuggestion suggestion = new TopicSuggestion();
        suggestion.setGroup(group);
        suggestion.setUser(user);
        suggestion.setText(text.trim());
        suggestion.setTargetDate(targetDate);
        return suggestionRepository.save(suggestion);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TopicSuggestion> mySuggestion(Long userId, Long groupId) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        return suggestionRepository.findByGroupIdAndUserIdAndTargetDate(group.getId(), userId, LocalDate.now(zone()).plusDays(1));
    }

    @Transactional
    public TopicSuggestion updateSuggestion(Long userId, Long suggestionId, String text) {
        TopicSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주제 제안을 찾을 수 없습니다."));
        if (!suggestion.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 제안한 주제만 수정할 수 있습니다.");
        }
        if (!suggestion.getTargetDate().equals(LocalDate.now(zone()).plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내일 주제 제안만 수정할 수 있습니다.");
        }
        suggestion.setText(text.trim());
        return suggestion;
    }

    @Transactional
    public void deleteSuggestion(Long userId, Long suggestionId) {
        TopicSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주제 제안을 찾을 수 없습니다."));
        if (!suggestion.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내가 제안한 주제만 삭제할 수 있습니다.");
        }
        if (!suggestion.getTargetDate().equals(LocalDate.now(zone()).plusDays(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내일 주제 제안만 삭제할 수 있습니다.");
        }
        suggestionRepository.delete(suggestion);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.time-zone:Asia/Seoul}")
    @Transactional
    public void selectDailyTopicsAtMidnight() {
        LocalDate today = LocalDate.now(zone());
        groupRepository.findAll().forEach(group -> ensureDailyTopic(group, today));
    }

    @Transactional
    public synchronized DailyTopic ensureDailyTopic(FriendGroup group, LocalDate date) {
        return dailyTopicRepository.findByGroupIdAndDate(group.getId(), date)
                .orElseGet(() -> {
                    try {
                        return dailyTopicRepository.saveAndFlush(newTopic(group, date));
                    } catch (DataIntegrityViolationException ignored) {
                        return dailyTopicRepository.findByGroupIdAndDate(group.getId(), date)
                                .orElseThrow(() -> ignored);
                    }
                });
    }

    private DailyTopic newTopic(FriendGroup group, LocalDate date) {
        List<TopicSuggestion> suggestions = suggestionRepository.findByGroupIdAndTargetDate(group.getId(), date);
        DailyTopic topic = new DailyTopic();
        topic.setGroup(group);
        topic.setDate(date);
        if (suggestions.isEmpty()) {
            topic.setText(DEFAULT_TOPICS.get(random.nextInt(DEFAULT_TOPICS.size())));
            topic.setFromSuggestion(false);
        } else {
            TopicSuggestion selected = suggestions.get(random.nextInt(suggestions.size()));
            topic.setText(selected.getText());
            topic.setFromSuggestion(true);
        }
        return topic;
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTimeZone());
    }
}
