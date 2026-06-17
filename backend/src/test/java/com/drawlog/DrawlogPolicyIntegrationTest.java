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
import com.drawlog.group.GroupService;
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
        drawingService.submitToday(owner.getId(), group.getId(), strokeJson(), thumbnail());

        drawingService.lockDrawingsBefore(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> drawingService.updateToday(owner.getId(), group.getId(), strokeJson(), thumbnail()))
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

        drawingService.submitToday(owner.getId(), group.getId(), strokeJson(), thumbnail());

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

    private User user(String prefix) {
        User user = new User();
        user.setNickname(prefix + System.nanoTime());
        user.setEmail(prefix + System.nanoTime() + "@example.com");
        user.setPasswordHash("password");
        return userRepository.save(user);
    }

    private MockMultipartFile image() {
        return image("drawing.webp");
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("image", filename, "image/webp", new byte[] {1, 2, 3});
    }

    private Path uploadedFile(String imageUrl) {
        String prefix = appProperties.getPublicUploadPath() + "/";
        assertThat(imageUrl).startsWith(prefix);
        return Path.of(appProperties.getUploadDir()).resolve(imageUrl.substring(prefix.length()));
    }

    private MockMultipartFile thumbnail() {
        return new MockMultipartFile("thumbnail", "drawing.webp", "image/webp", new byte[] {1, 2, 3});
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
