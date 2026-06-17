package com.drawlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drawlog.chat.ChatDtos;
import com.drawlog.chat.ChatMessageRepository;
import com.drawlog.chat.ChatMessageType;
import com.drawlog.chat.ChatService;
import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingController;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.drawing.DrawingService;
import com.drawlog.feed.FeedService;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.FriendGroupRepository;
import com.drawlog.group.GroupMemberRepository;
import com.drawlog.group.GroupService;
import com.drawlog.group.MemberRole;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.topic.TopicDtos;
import com.drawlog.topic.TopicService;
import com.drawlog.topic.TopicVoteRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import com.drawlog.user.UserService;
import com.drawlog.user.UserStatus;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DrawlogPolicyIntegrationTest {
    @Autowired GroupService groupService;
    @Autowired TopicService topicService;
    @Autowired DrawingService drawingService;
    @Autowired FeedService feedService;
    @Autowired DrawingRepository drawingRepository;
    @Autowired ChatService chatService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired TopicVoteRepository voteRepository;
    @Autowired DailyTopicRepository dailyTopicRepository;
    @Autowired ChatMessageRepository chatMessageRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AppProperties appProperties;
    @Autowired GroupMemberRepository memberRepository;
    @Autowired FriendGroupRepository groupRepository;

    @BeforeEach
    @AfterEach
    void cleanUploads() throws IOException {
        Path uploadDir = Path.of(appProperties.getUploadDir());
        if (!Files.exists(uploadDir)) return;
        try (var paths = Files.walk(uploadDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(uploadDir))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    @Test
    void groupMaxMembersIsLimitedToTwelve() {
        User owner = user("max-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "열두명", 12, null);
        for (int i = 0; i < 11; i++) {
            groupService.joinGroup(user("member-" + i).getId(), group.getInviteCode());
        }

        assertThatThrownBy(() -> groupService.joinGroup(user("overflow").getId(), group.getInviteCode()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GROUP_FULL);
    }

    @Test
    void ownerCannotLeaveBeforeTransfer() {
        User owner = user("owner-leave");
        FriendGroup group = groupService.createGroup(owner.getId(), "위임방", 6, null);
        groupService.joinGroup(user("friend-leave").getId(), group.getInviteCode());

        assertThatThrownBy(() -> groupService.leave(owner.getId(), group.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.OWNER_TRANSFER_REQUIRED);
    }

    @Test
    void topicSuggestionIsOnePerUserAndLockedAfterVote() {
        User owner = user("topic-owner");
        User friend = user("topic-friend");
        FriendGroup group = groupService.createGroup(owner.getId(), "주제방", 6, null);
        groupService.joinGroup(friend.getId(), group.getInviteCode());
        LocalDate targetDate = LocalDate.now().plusDays(1);

        TopicDtos.SuggestionResponse suggestion = topicService.suggest(owner.getId(), group.getId(), targetDate, "바다");
        assertThatThrownBy(() -> topicService.suggest(owner.getId(), group.getId(), targetDate, "하늘"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.TOPIC_ALREADY_EXISTS);

        topicService.vote(friend.getId(), group.getId(), suggestion.id());

        assertThatThrownBy(() -> topicService.update(owner.getId(), group.getId(), suggestion.id(), "수정"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.TOPIC_LOCKED_BY_VOTE);
        assertThatThrownBy(() -> topicService.delete(owner.getId(), group.getId(), suggestion.id()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.TOPIC_LOCKED_BY_VOTE);
    }

    @Test
    void revotingReplacesExistingVote() {
        User owner = user("vote-owner");
        User friend = user("vote-friend");
        FriendGroup group = groupService.createGroup(owner.getId(), "투표방", 6, null);
        groupService.joinGroup(friend.getId(), group.getInviteCode());
        LocalDate targetDate = LocalDate.now().plusDays(1);
        TopicDtos.SuggestionResponse first = topicService.suggest(owner.getId(), group.getId(), targetDate, "첫 후보");
        TopicDtos.SuggestionResponse second = topicService.suggest(friend.getId(), group.getId(), targetDate, "둘 후보");

        topicService.vote(owner.getId(), group.getId(), first.id());
        topicService.vote(owner.getId(), group.getId(), second.id());

        assertThat(voteRepository.findByGroupIdAndUserIdAndTargetDate(group.getId(), owner.getId(), targetDate))
                .get()
                .extracting(vote -> vote.getSuggestion().getId())
                .isEqualTo(second.id());
    }

    @Test
    void dailyTopicSelectionHandlesVotesTiesZeroVotesAndDefaults() {
        User owner = user("select-owner");
        User friend = user("select-friend");
        User third = user("select-third");
        FriendGroup voted = groupService.createGroup(owner.getId(), "득표방", 6, null);
        groupService.joinGroup(friend.getId(), voted.getInviteCode());
        groupService.joinGroup(third.getId(), voted.getInviteCode());
        LocalDate votedDate = LocalDate.now().plusDays(3);
        TopicDtos.SuggestionResponse a = topicService.suggest(owner.getId(), voted.getId(), votedDate, "A");
        TopicDtos.SuggestionResponse b = topicService.suggest(friend.getId(), voted.getId(), votedDate, "B");
        topicService.vote(owner.getId(), voted.getId(), a.id());
        topicService.vote(friend.getId(), voted.getId(), b.id());
        DailyTopic tied = topicService.selectDailyTopic(voted, votedDate);
        assertThat(tied.getText()).isIn("A", "B");

        FriendGroup zero = groupService.createGroup(owner.getId(), "무득표방", 6, null);
        groupService.joinGroup(friend.getId(), zero.getInviteCode());
        LocalDate zeroDate = LocalDate.now().plusDays(4);
        topicService.suggest(owner.getId(), zero.getId(), zeroDate, "C");
        topicService.suggest(friend.getId(), zero.getId(), zeroDate, "D");
        DailyTopic zeroSelected = topicService.selectDailyTopic(zero, zeroDate);
        assertThat(zeroSelected.getText()).isIn("C", "D");

        FriendGroup empty = groupService.createGroup(owner.getId(), "기본방", 6, null);
        DailyTopic defaultTopic = topicService.selectDailyTopic(empty, LocalDate.now().plusDays(5));
        assertThat(defaultTopic.getSelectedSuggestion()).isNull();
        assertThat(defaultTopic.getText()).isNotBlank();
    }

    @Test
    void drawingCanOnlyBeUpdatedBeforeItIsLockedAndDeleteApiIsAbsent() {
        User owner = user("draw-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "그림방", 6, "오늘 그림");
        drawingService.submitToday(owner.getId(), group.getId(), image());

        drawingService.lockDrawingsBefore(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> drawingService.updateToday(owner.getId(), group.getId(), image()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DRAWING_LOCKED);
        assertThat(Arrays.stream(DrawingController.class.getDeclaredMethods()).map(Method::getName).toList())
                .doesNotContain("delete");
    }

    @Test
    void drawingUpdateDeletesPreviousImageFile() {
        User owner = user("draw-file-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "그림파일방", 6, "오늘 그림");
        Drawing first = drawingService.submitToday(owner.getId(), group.getId(), image("first.webp"));
        Path firstFile = uploadedFile(first.getImagePath());

        assertThat(firstFile).exists();

        Drawing second = drawingService.updateToday(owner.getId(), group.getId(), image("second.webp"));
        Path secondFile = uploadedFile(second.getImagePath());

        assertThat(firstFile).doesNotExist();
        assertThat(secondFile).exists();
    }

    @Test
    void todayFeedIsLockedUntilSubmitterDraws() {
        User owner = user("feed-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "피드방", 6, "오늘 피드");

        assertThat(feedService.feed(owner.getId(), group.getId(), LocalDate.now()).feedLocked()).isTrue();

        drawingService.submitToday(owner.getId(), group.getId(), image());

        assertThat(feedService.feed(owner.getId(), group.getId(), LocalDate.now()).feedLocked()).isFalse();
    }

    @Test
    void emptyFeedDateDoesNotCreateDailyTopic() {
        User owner = user("empty-feed-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "빈날방", 6, null);
        LocalDate emptyDate = LocalDate.now().minusDays(3);

        assertThat(dailyTopicRepository.findByGroupIdAndTopicDate(group.getId(), emptyDate)).isEmpty();

        var feed = feedService.feed(owner.getId(), group.getId(), emptyDate);

        assertThat(feed.dailyTopic()).isNull();
        assertThat(feed.members()).hasSize(1);
        assertThat(feed.members().get(0).drawing()).isNull();
        assertThat(dailyTopicRepository.findByGroupIdAndTopicDate(group.getId(), emptyDate)).isEmpty();
    }

    @Test
    void futureFeedDateIsRejectedAndDoesNotCreateDailyTopic() {
        User owner = user("future-feed-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "미래방", 6, null);
        LocalDate future = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> feedService.feed(owner.getId(), group.getId(), future))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(dailyTopicRepository.findByGroupIdAndTopicDate(group.getId(), future)).isEmpty();
    }

    @Test
    void feedDatesOnlyIncludeDatesThatHaveDrawings() {
        User owner = user("record-date-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "기록날방", 6, null);
        LocalDate first = LocalDate.now().minusDays(7);
        LocalDate empty = LocalDate.now().minusDays(6);
        LocalDate second = LocalDate.now().minusDays(5);
        saveDrawing(owner, group, first, "첫 기록");
        DailyTopic emptyTopic = new DailyTopic();
        emptyTopic.setGroup(group);
        emptyTopic.setTopicDate(empty);
        emptyTopic.setText("빈 날짜");
        dailyTopicRepository.save(emptyTopic);
        saveDrawing(owner, group, second, "둘 기록");

        assertThat(feedService.dates(owner.getId(), group.getId()).dates()).contains(first, second).doesNotContain(empty);
    }

    @Test
    void chatDeleteSoftDeletesMessage() {
        User owner = user("chat-owner");
        FriendGroup group = groupService.createGroup(owner.getId(), "채팅방", 6, null);

        ChatDtos.ChatMessageResponse message = chatService.send(owner.getId(), group.getId(),
                new ChatDtos.SendMessageRequest(ChatMessageType.TEXT, "안녕", null));
        chatService.delete(owner.getId(), group.getId(), message.id());

        assertThat(chatMessageRepository.findById(message.id())).get()
                .extracting("deletedAt")
                .isNotNull();
    }

    @Test
    void chatReplyReferencesMessageAndHidesDeletedOriginal() {
        User owner = user("reply-owner");
        User member = user("reply-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "답장방", 6, null);
        groupService.joinGroup(member.getId(), group.getInviteCode());

        ChatDtos.ChatMessageResponse original = chatService.send(owner.getId(), group.getId(),
                new ChatDtos.SendMessageRequest(ChatMessageType.TEXT, "오늘 그림 다 그렸어", null, null));
        ChatDtos.ChatMessageResponse reply = chatService.send(member.getId(), group.getId(),
                new ChatDtos.SendMessageRequest(ChatMessageType.TEXT, "나도 완료했어", null, original.id()));

        assertThat(reply.replyTo()).isNotNull();
        assertThat(reply.replyTo().id()).isEqualTo(original.id());
        assertThat(reply.replyTo().content()).isEqualTo("오늘 그림 다 그렸어");

        chatService.delete(owner.getId(), group.getId(), original.id());

        ChatDtos.ChatMessageResponse afterDelete = chatService.messages(member.getId(), group.getId(), null, 30)
                .messages()
                .stream()
                .filter(message -> message.id().equals(reply.id()))
                .findFirst()
                .orElseThrow();
        assertThat(afterDelete.replyTo()).isNull();
    }

    @Test
    void profileImageUpdateDeletesPreviousFile() {
        User user = user("profile-update");

        var first = userService.updateProfileImage(user.getId(), image("profile-a.webp"));
        Path firstFile = uploadedFile(first.profileImageUrl());
        assertThat(firstFile).exists();

        var second = userService.updateProfileImage(user.getId(), image("profile-b.webp"));
        Path secondFile = uploadedFile(second.profileImageUrl());

        assertThat(firstFile).doesNotExist();
        assertThat(secondFile).exists();
    }

    @Test
    void profileImageDeleteClearsDbAndDeletesFile() {
        User user = user("profile-delete");
        var updated = userService.updateProfileImage(user.getId(), image("profile-delete.webp"));
        Path profileFile = uploadedFile(updated.profileImageUrl());
        assertThat(profileFile).exists();

        var deleted = userService.deleteProfileImage(user.getId());

        assertThat(deleted.profileImageUrl()).isNull();
        assertThat(profileFile).doesNotExist();
    }

    @Test
    void accountDeletionIsSoftDeletion() {
        User user = user("delete-me");
        var updated = userService.updateProfileImage(user.getId(), image("profile-withdraw.webp"));
        Path profileFile = uploadedFile(updated.profileImageUrl());
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash("delete-token-" + System.nanoTime());
        token.setExpiresAt(Instant.now().plusSeconds(60));
        refreshTokenRepository.save(token);

        userService.deleteMe(user.getId());

        assertThat(profileFile).doesNotExist();
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).get()
                .satisfies(deleted -> {
                    assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
                    assertThat(deleted.getDeletedAt()).isNotNull();
                    assertThat(deleted.getEmail()).startsWith("deleted_user_");
                    assertThat(deleted.getNickname()).isEqualTo("탈퇴한 사용자");
                });
    }

    @Test
    void memberAccountDeletionRemovesMembership() {
        User owner = user("withdraw-member-owner");
        User member = user("withdraw-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "탈퇴멤버방", 6, null);
        groupService.joinGroup(member.getId(), group.getInviteCode());

        userService.deleteMe(member.getId());

        assertThat(memberRepository.existsByGroupIdAndUserId(group.getId(), member.getId())).isFalse();
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), member.getId())).isEmpty();
        assertThat(groupService.members(owner.getId(), group.getId()))
                .extracting("userId")
                .doesNotContain(member.getId());
        assertThat(groupService.groupDetailsForUser(owner.getId()))
                .filteredOn(detail -> detail.id().equals(group.getId()))
                .singleElement()
                .satisfies(detail -> assertThat(detail.members()).extracting("userId").doesNotContain(member.getId()));
    }

    @Test
    void accountDeletionCleansMembershipsByUserIdEvenAfterEmailAndNicknameChange() {
        User owner = user("id-cleanup-owner");
        User member = user("id-cleanup-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "아이디정리방", 6, null);
        groupService.joinGroup(member.getId(), group.getInviteCode());
        Long memberId = member.getId();

        member.setEmail("renamed-" + memberId + "@example.com");
        member.setNickname("바뀐닉네임");

        userService.deleteMe(memberId);

        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), memberId)).isEmpty();
        assertThat(groupService.members(owner.getId(), group.getId()))
                .extracting("userId")
                .doesNotContain(memberId);
        assertThat(groupService.groupDetailsForUser(owner.getId()))
                .filteredOn(detail -> detail.id().equals(group.getId()))
                .singleElement()
                .satisfies(detail -> assertThat(detail.members()).extracting("userId").doesNotContain(memberId));
        assertThat(userRepository.findById(memberId)).get()
                .satisfies(deleted -> {
                    assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
                    assertThat(deleted.getEmail()).startsWith("deleted_user_" + memberId);
                    assertThat(deleted.getNickname()).isEqualTo("탈퇴한 사용자");
                });
    }

    @Test
    void deletedUsersAreHiddenFromMembersAndFeedEvenIfMembershipRemains() {
        User owner = user("stale-owner");
        User deletedMember = user("stale-deleted-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "스테일멤버방", 6, "오늘 주제");
        groupService.joinGroup(deletedMember.getId(), group.getInviteCode());
        drawingService.submitToday(owner.getId(), group.getId(), image("owner-visible.webp"));
        drawingService.submitToday(deletedMember.getId(), group.getId(), image("deleted-hidden.webp"));

        deletedMember.setStatus(UserStatus.DELETED);
        deletedMember.setNickname("탈퇴한 사용자");

        assertThat(memberRepository.existsByGroupIdAndUserId(group.getId(), deletedMember.getId())).isTrue();
        assertThat(groupService.members(owner.getId(), group.getId()))
                .extracting("userId")
                .doesNotContain(deletedMember.getId());
        assertThat(feedService.feed(owner.getId(), group.getId(), LocalDate.now()).members())
                .extracting("userId")
                .doesNotContain(deletedMember.getId());
    }

    @Test
    void ownerAccountDeletionTransfersOwnershipToOldestRemainingMember() {
        User owner = user("withdraw-owner");
        User firstMember = user("withdraw-first-member");
        User secondMember = user("withdraw-second-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "자동위임방", 6, null);
        groupService.joinGroup(firstMember.getId(), group.getInviteCode());
        groupService.joinGroup(secondMember.getId(), group.getInviteCode());

        userService.deleteMe(owner.getId());

        assertThat(memberRepository.existsByGroupIdAndUserId(group.getId(), owner.getId())).isFalse();
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), owner.getId())).isEmpty();
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), firstMember.getId()))
                .get()
                .extracting("role")
                .isEqualTo(MemberRole.OWNER);
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), secondMember.getId()))
                .get()
                .extracting("role")
                .isEqualTo(MemberRole.MEMBER);
    }

    @Test
    void ownerAccountDeletionSkipsDeletedMembersWhenSelectingSuccessor() {
        User owner = user("withdraw-owner-stale");
        User deletedOldestMember = user("withdraw-deleted-oldest");
        User activeMember = user("withdraw-active-successor");
        FriendGroup group = groupService.createGroup(owner.getId(), "삭제회원스킵방", 6, null);
        groupService.joinGroup(deletedOldestMember.getId(), group.getInviteCode());
        groupService.joinGroup(activeMember.getId(), group.getInviteCode());
        deletedOldestMember.setStatus(UserStatus.DELETED);
        deletedOldestMember.setNickname("탈퇴한 사용자");

        userService.deleteMe(owner.getId());

        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), activeMember.getId()))
                .get()
                .extracting("role")
                .isEqualTo(MemberRole.OWNER);
        assertThat(groupService.members(activeMember.getId(), group.getId()))
                .extracting("userId")
                .doesNotContain(deletedOldestMember.getId());
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), deletedOldestMember.getId())).isEmpty();
    }

    @Test
    void ownerCannotTransferOrRemoveDeletedMember() {
        User owner = user("deleted-target-owner");
        User deletedMember = user("deleted-target-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "삭제타겟방", 6, null);
        groupService.joinGroup(deletedMember.getId(), group.getInviteCode());
        deletedMember.setStatus(UserStatus.DELETED);

        assertThatThrownBy(() -> groupService.transferOwner(owner.getId(), group.getId(), deletedMember.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
        assertThatThrownBy(() -> groupService.removeMember(owner.getId(), group.getId(), deletedMember.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    void staleDeletedOwnerMembershipIsRemovedAndOldestActiveMemberBecomesOwner() {
        User deletedOwner = user("stale-deleted-owner");
        User firstActiveMember = user("stale-first-active");
        User secondActiveMember = user("stale-second-active");
        FriendGroup group = groupService.createGroup(deletedOwner.getId(), "스테일방장방", 6, null);
        groupService.joinGroup(firstActiveMember.getId(), group.getInviteCode());
        groupService.joinGroup(secondActiveMember.getId(), group.getInviteCode());
        deletedOwner.setStatus(UserStatus.DELETED);
        deletedOwner.setNickname("탈퇴한 사용자");

        var details = groupService.groupDetailsForUser(firstActiveMember.getId());

        assertThat(details)
                .filteredOn(detail -> detail.id().equals(group.getId()))
                .singleElement()
                .satisfies(detail -> {
                    assertThat(detail.owner()).isTrue();
                    assertThat(detail.members()).extracting("userId").containsExactly(firstActiveMember.getId(), secondActiveMember.getId());
                    assertThat(detail.members()).extracting("nickname").doesNotContain("탈퇴한 사용자");
                });
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), deletedOwner.getId())).isEmpty();
        assertThat(memberRepository.findByGroupIdAndUserId(group.getId(), firstActiveMember.getId()))
                .get()
                .extracting("role")
                .isEqualTo(MemberRole.OWNER);
    }

    @Test
    void soleOwnerAccountDeletionDeletesEmptyGroup() {
        User owner = user("solo-owner-delete");
        FriendGroup group = groupService.createGroup(owner.getId(), "혼자방", 6, "혼자 주제");
        chatService.send(owner.getId(), group.getId(), new ChatDtos.SendMessageRequest(ChatMessageType.TEXT, "혼자 남긴 말", null, null));

        userService.deleteMe(owner.getId());

        assertThat(groupRepository.findById(group.getId())).isEmpty();
        assertThat(memberRepository.countByGroupId(group.getId())).isZero();
    }

    @Test
    void accountDeletionDeletesDrawingsFilesAndRemovesUserFromFeed() {
        User owner = user("withdraw-feed-owner");
        User member = user("withdraw-feed-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "피드탈퇴방", 6, "오늘 피드");
        groupService.joinGroup(member.getId(), group.getInviteCode());
        drawingService.submitToday(owner.getId(), group.getId(), image("owner-drawing.webp"));
        Drawing memberDrawing = drawingService.submitToday(member.getId(), group.getId(), image("member-drawing.webp"));
        Path memberDrawingFile = uploadedFile(memberDrawing.getImagePath());

        assertThat(memberDrawingFile).exists();

        userService.deleteMe(member.getId());

        assertThat(drawingRepository.findById(memberDrawing.getId())).isEmpty();
        assertThat(memberDrawingFile).doesNotExist();
        assertThat(feedService.feed(owner.getId(), group.getId(), LocalDate.now()).members())
                .extracting("userId")
                .doesNotContain(member.getId());
    }

    @Test
    void accountDeletionHidesDeletedUserMessagesAndKeepsRemainingChatUsable() {
        User owner = user("withdraw-chat-owner");
        User member = user("withdraw-chat-member");
        FriendGroup group = groupService.createGroup(owner.getId(), "채팅탈퇴방", 6, "오늘 채팅");
        groupService.joinGroup(member.getId(), group.getInviteCode());
        Drawing drawing = drawingService.submitToday(member.getId(), group.getId(), image("quoted-before-delete.webp"));
        ChatDtos.ChatMessageResponse memberMessage = chatService.send(member.getId(), group.getId(),
                new ChatDtos.SendMessageRequest(ChatMessageType.TEXT, "나 탈퇴 전 메시지", null, null));
        ChatDtos.ChatMessageResponse quoteMessage = chatService.send(owner.getId(), group.getId(),
                new ChatDtos.SendMessageRequest(ChatMessageType.DRAWING_QUOTE, "이 그림 좋다", drawing.getId(), null));
        ChatDtos.ChatMessageResponse replyMessage = chatService.send(owner.getId(), group.getId(),
                new ChatDtos.SendMessageRequest(ChatMessageType.TEXT, "답장도 남아야 함", null, memberMessage.id()));

        userService.deleteMe(member.getId());

        var messages = chatService.messages(owner.getId(), group.getId(), null, 30).messages();
        assertThat(messages).extracting("id").doesNotContain(memberMessage.id());
        assertThat(messages)
                .filteredOn(message -> message.id().equals(quoteMessage.id()))
                .singleElement()
                .extracting("quote")
                .isNull();
        assertThat(messages)
                .filteredOn(message -> message.id().equals(replyMessage.id()))
                .singleElement()
                .extracting("replyTo")
                .isNull();
    }

    private User user(String prefix) {
        User user = new User();
        user.setNickname(prefix + System.nanoTime());
        user.setEmail(prefix + System.nanoTime() + "@example.com");
        user.setPasswordHash("password");
        return userRepository.save(user);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("image", "drawing.webp", "image/webp", new byte[] {1, 2, 3});
    }

    private void saveDrawing(User user, FriendGroup group, LocalDate date, String topicText) {
        DailyTopic topic = new DailyTopic();
        topic.setGroup(group);
        topic.setTopicDate(date);
        topic.setText(topicText);
        dailyTopicRepository.save(topic);
        Drawing drawing = new Drawing();
        drawing.setUser(user);
        drawing.setGroup(group);
        drawing.setDailyTopic(topic);
        drawing.setImagePath("/uploads/" + topicText + ".webp");
        drawingRepository.save(drawing);
    }
}
