package com.drawlog.user;

import com.drawlog.auth.RefreshTokenRepository;
import com.drawlog.chat.ChatMessageRepository;
import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.FriendGroupRepository;
import com.drawlog.group.GroupMember;
import com.drawlog.group.GroupMemberRepository;
import com.drawlog.group.MemberRole;
import com.drawlog.notification.GroupNotificationSettingsRepository;
import com.drawlog.notification.NotificationRepository;
import com.drawlog.storage.StorageService;
import com.drawlog.storage.StoredFile;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.topic.TopicSuggestionRepository;
import com.drawlog.topic.TopicVoteRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GroupMemberRepository memberRepository;
    private final FriendGroupRepository groupRepository;
    private final DrawingRepository drawingRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DailyTopicRepository dailyTopicRepository;
    private final TopicSuggestionRepository topicSuggestionRepository;
    private final TopicVoteRepository topicVoteRepository;
    private final NotificationRepository notificationRepository;
    private final GroupNotificationSettingsRepository groupNotificationSettingsRepository;

    public UserService(UserRepository userRepository,
                       StorageService storageService,
                       RefreshTokenRepository refreshTokenRepository,
                       GroupMemberRepository memberRepository,
                       FriendGroupRepository groupRepository,
                       DrawingRepository drawingRepository,
                       ChatMessageRepository chatMessageRepository,
                       DailyTopicRepository dailyTopicRepository,
                       TopicSuggestionRepository topicSuggestionRepository,
                       TopicVoteRepository topicVoteRepository,
                       NotificationRepository notificationRepository,
                       GroupNotificationSettingsRepository groupNotificationSettingsRepository) {
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.drawingRepository = drawingRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.dailyTopicRepository = dailyTopicRepository;
        this.topicSuggestionRepository = topicSuggestionRepository;
        this.topicVoteRepository = topicVoteRepository;
        this.notificationRepository = notificationRepository;
        this.groupNotificationSettingsRepository = groupNotificationSettingsRepository;
    }

    @Transactional
    public UserDtos.UserResponse updateNickname(Long userId, String nickname) {
        User user = user(userId);
        user.setNickname(nickname.trim());
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateProfileImage(Long userId, MultipartFile image) {
        User user = user(userId);
        String oldImage = user.getProfileImageUrl();
        StoredFile storedFile = storageService.storeImage(image);
        user.setProfileImageUrl(storedFile.imageUrl());
        try {
            userRepository.flush();
        } catch (RuntimeException e) {
            storageService.deleteImage(storedFile.imageUrl());
            throw e;
        }
        if (oldImage != null) storageService.deleteImage(oldImage);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse deleteProfileImage(Long userId) {
        User user = user(userId);
        String oldImage = user.getProfileImageUrl();
        user.setProfileImageUrl(null);
        userRepository.flush();
        if (oldImage != null) storageService.deleteImage(oldImage);
        return toResponse(user);
    }

    @Transactional
    public void deleteMe(Long userId) {
        User user = user(userId);
        Long deletingUserId = user.getId();
        String oldImage = user.getProfileImageUrl();

        leaveAllGroupsForAccountDeletion(deletingUserId);
        deleteUserDrawings(deletingUserId);
        hideUserChatMessages(deletingUserId);
        groupNotificationSettingsRepository.deleteByUserId(deletingUserId);
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(deletingUserId)
                .forEach(token -> token.setRevokedAt(Instant.now()));
        if (oldImage != null) storageService.deleteImage(oldImage);

        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());
        user.setEmail("deleted_user_" + user.getId() + "@drawlog.local");
        user.setNickname("탈퇴한 사용자");
        user.setProfileImageUrl(null);
    }

    private void deleteUserDrawings(Long userId) {
        List<Drawing> drawings = drawingRepository.findByUserId(userId);
        if (drawings.isEmpty()) return;
        List<Long> drawingIds = drawings.stream().map(Drawing::getId).toList();
        clearDrawingReferences(drawingIds);
        drawings.forEach(drawing -> storageService.deleteImage(drawing.getImagePath()));
        drawingRepository.deleteAll(drawings);
    }

    private void hideUserChatMessages(Long userId) {
        chatMessageRepository.clearReplyReferencesToSenderMessages(userId);
        Instant deletedAt = Instant.now();
        chatMessageRepository.findBySenderIdAndDeletedAtIsNull(userId)
                .forEach(message -> message.setDeletedAt(deletedAt));
    }

    private void leaveAllGroupsForAccountDeletion(Long userId) {
        List<GroupMember> memberships = memberRepository.findByUserIdOrderByJoinedAtAsc(userId);
        for (GroupMember membership : memberships) {
            FriendGroup group = membership.getGroup();
            List<GroupMember> groupMembers = memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId());
            if (membership.getRole() == MemberRole.OWNER) {
                GroupMember successor = groupMembers.stream()
                        .filter(member -> !member.getUser().getId().equals(userId))
                        .filter(member -> member.getUser().getStatus() == UserStatus.ACTIVE)
                        .findFirst()
                        .orElse(null);
                if (successor == null) {
                    deleteEmptyOwnedGroup(group);
                    continue;
                }
                successor.setRole(MemberRole.OWNER);
            }
            memberRepository.delete(membership);
        }
        memberRepository.deleteAllByUserId(userId);
        memberRepository.flush();
    }

    private void deleteEmptyOwnedGroup(FriendGroup group) {
        Long groupId = group.getId();
        deleteGroupDrawings(groupId);
        chatMessageRepository.clearReplyReferencesForGroup(groupId);
        chatMessageRepository.deleteByGroupId(groupId);
        notificationRepository.deleteByGroupId(groupId);
        groupNotificationSettingsRepository.deleteByGroupId(groupId);
        dailyTopicRepository.clearSelectedSuggestionsForGroup(groupId);
        dailyTopicRepository.deleteByGroupId(groupId);
        topicVoteRepository.deleteByGroupId(groupId);
        topicSuggestionRepository.deleteByGroupId(groupId);
        memberRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);
    }

    private void deleteGroupDrawings(Long groupId) {
        List<Drawing> drawings = drawingRepository.findByGroupId(groupId);
        if (drawings.isEmpty()) return;
        List<Long> drawingIds = drawings.stream().map(Drawing::getId).toList();
        clearDrawingReferences(drawingIds);
        drawings.forEach(drawing -> storageService.deleteImage(drawing.getImagePath()));
        drawingRepository.deleteAll(drawings);
    }

    private void clearDrawingReferences(List<Long> drawingIds) {
        chatMessageRepository.findByDrawingIdIn(drawingIds).forEach(message -> message.setDrawing(null));
        chatMessageRepository.clearDrawingReferences(drawingIds);
    }

    public UserDtos.UserResponse toResponse(User user) {
        return new UserDtos.UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getStatus()
        );
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
