package com.drawlog.notification;

import com.drawlog.chat.ChatMessage;
import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.FriendGroupRepository;
import com.drawlog.group.GroupMember;
import com.drawlog.group.GroupMemberRepository;
import com.drawlog.group.GroupService;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.TopicSuggestionRepository;
import com.drawlog.topic.TopicVoteRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import com.drawlog.user.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserNotificationSettingsRepository userSettingsRepository;
    private final GroupNotificationSettingsRepository groupSettingsRepository;
    private final GroupMemberRepository memberRepository;
    private final FriendGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final DrawingRepository drawingRepository;
    private final TopicSuggestionRepository suggestionRepository;
    private final TopicVoteRepository voteRepository;
    private final GroupService groupService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserNotificationSettingsRepository userSettingsRepository,
                               GroupNotificationSettingsRepository groupSettingsRepository,
                               GroupMemberRepository memberRepository,
                               FriendGroupRepository groupRepository,
                               UserRepository userRepository,
                               DrawingRepository drawingRepository,
                               TopicSuggestionRepository suggestionRepository,
                               TopicVoteRepository voteRepository,
                               GroupService groupService) {
        this.notificationRepository = notificationRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.groupSettingsRepository = groupSettingsRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.drawingRepository = drawingRepository;
        this.suggestionRepository = suggestionRepository;
        this.voteRepository = voteRepository;
        this.groupService = groupService;
    }

    @Transactional(readOnly = true)
    public List<NotificationDtos.NotificationResponse> notifications(Long userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void readAll(Long userId) {
        Instant now = Instant.now();
        notificationRepository.findByUserIdAndReadAtIsNull(userId).forEach(notification -> notification.setReadAt(now));
    }

    @Transactional
    public NotificationDtos.SettingsResponse settings(Long userId) {
        User user = user(userId);
        UserNotificationSettings settings = userSettingsRepository.findById(userId).orElseGet(() -> {
            UserNotificationSettings created = new UserNotificationSettings();
            created.setUser(user);
            return userSettingsRepository.save(created);
        });
        List<NotificationDtos.GroupSettingResponse> groups = memberRepository.findByUserIdAndUserStatusOrderByJoinedAtAsc(userId, UserStatus.ACTIVE).stream()
                .map(member -> new NotificationDtos.GroupSettingResponse(
                        member.getGroup().getId(),
                        member.getGroup().getName(),
                        groupSettingsRepository.findByUserIdAndGroupId(userId, member.getGroup().getId())
                                .map(GroupNotificationSettings::isEnabled)
                                .orElse(true)
                ))
                .toList();
        return new NotificationDtos.SettingsResponse(settings.isAllEnabled(), groups);
    }

    @Transactional
    public NotificationDtos.SettingsResponse updateUserSettings(Long userId, boolean enabled) {
        User user = user(userId);
        UserNotificationSettings settings = userSettingsRepository.findById(userId).orElseGet(() -> {
            UserNotificationSettings created = new UserNotificationSettings();
            created.setUser(user);
            return created;
        });
        settings.setAllEnabled(enabled);
        userSettingsRepository.save(settings);
        return settings(userId);
    }

    @Transactional
    public NotificationDtos.GroupSettingResponse updateGroupSettings(Long userId, Long groupId, boolean enabled) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        User user = user(userId);
        GroupNotificationSettings settings = groupSettingsRepository.findByUserIdAndGroupId(userId, groupId).orElseGet(() -> {
            GroupNotificationSettings created = new GroupNotificationSettings();
            created.setUser(user);
            created.setGroup(group);
            return created;
        });
        settings.setEnabled(enabled);
        groupSettingsRepository.save(settings);
        return new NotificationDtos.GroupSettingResponse(group.getId(), group.getName(), settings.isEnabled());
    }

    @Transactional
    public void notifyChat(ChatMessage message) {
        for (GroupMember member : memberRepository.findByGroupIdAndUserStatusOrderByJoinedAtAsc(message.getGroup().getId(), UserStatus.ACTIVE)) {
            if (!member.getUser().getId().equals(message.getSender().getId())) {
                createIfEnabled(
                        member.getUser(),
                        message.getGroup(),
                        "CHAT",
                        message.getGroup().getName(),
                        message.getSender().getNickname() + "님의 새 메시지",
                        "CHAT",
                        message.getId()
                );
            }
        }
    }

    @Transactional
    public void notifyDrawingUploaded(Drawing drawing) {
        DailyTopic topic = drawing.getDailyTopic();
        for (GroupMember member : memberRepository.findByGroupIdAndUserStatusOrderByJoinedAtAsc(drawing.getGroup().getId(), UserStatus.ACTIVE)) {
            Long recipientId = member.getUser().getId();
            if (recipientId.equals(drawing.getUser().getId())) continue;
            boolean recipientSubmitted = drawingRepository.existsByGroupIdAndDailyTopicIdAndUserId(
                    drawing.getGroup().getId(),
                    topic.getId(),
                    recipientId
            );
            if (recipientSubmitted) {
                createIfEnabled(
                        member.getUser(),
                        drawing.getGroup(),
                        "DRAWING_UPLOADED",
                        drawing.getGroup().getName(),
                        drawing.getUser().getNickname() + "님이 오늘 그림을 올렸어요.",
                        "DRAWING",
                        drawing.getId()
                );
            }
        }
    }

    @Transactional
    public void notifyUndrawnToday(LocalDate date) {
        for (FriendGroup group : groupRepository.findAll()) {
            for (GroupMember member : memberRepository.findByGroupIdAndUserStatusOrderByJoinedAtAsc(group.getId(), UserStatus.ACTIVE)) {
                boolean submitted = drawingRepository.existsByGroupIdAndDailyTopicTopicDateAndUserId(group.getId(), date, member.getUser().getId());
                if (!submitted) {
                    createIfEnabled(
                            member.getUser(),
                            group,
                            "DRAWING_REMINDER",
                            "오늘 그림을 남겨볼까요?",
                            group.getName() + "의 오늘 그림이 아직 비어 있어요.",
                            "GROUP",
                            group.getId()
                    );
                }
            }
        }
    }

    @Transactional
    public void notifyUnvoted(LocalDate targetDate) {
        for (FriendGroup group : groupRepository.findAll()) {
            if (suggestionRepository.findByGroupIdAndTargetDateOrderByCreatedAtAsc(group.getId(), targetDate).isEmpty()) continue;
            for (GroupMember member : memberRepository.findByGroupIdAndUserStatusOrderByJoinedAtAsc(group.getId(), UserStatus.ACTIVE)) {
                boolean voted = voteRepository.findByGroupIdAndUserIdAndTargetDate(group.getId(), member.getUser().getId(), targetDate).isPresent();
                if (!voted) {
                    createIfEnabled(
                            member.getUser(),
                            group,
                            "TOPIC_VOTE_REMINDER",
                            "내일 주제 투표 마감 전이에요.",
                            group.getName() + "에서 내일 주제를 골라주세요.",
                            "GROUP",
                            group.getId()
                    );
                }
            }
        }
    }

    private void createIfEnabled(User user, FriendGroup group, String type, String title, String message, String targetType, Long targetId) {
        if (user.getStatus() != UserStatus.ACTIVE) return;
        boolean allEnabled = userSettingsRepository.findById(user.getId())
                .map(UserNotificationSettings::isAllEnabled)
                .orElse(true);
        boolean groupEnabled = group == null || groupSettingsRepository.findByUserIdAndGroupId(user.getId(), group.getId())
                .map(GroupNotificationSettings::isEnabled)
                .orElse(true);
        if (!allEnabled || !groupEnabled) return;
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setGroup(group);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notificationRepository.save(notification);
    }

    private NotificationDtos.NotificationResponse toResponse(Notification notification) {
        return new NotificationDtos.NotificationResponse(
                notification.getId(),
                notification.getGroup() == null ? null : notification.getGroup().getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
