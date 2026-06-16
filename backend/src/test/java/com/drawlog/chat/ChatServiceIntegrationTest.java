package com.drawlog.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.GroupService;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatServiceIntegrationTest {
    @Autowired
    ChatService chatService;

    @Autowired
    ChatMessageRepository chatMessageRepository;

    @Autowired
    DrawingRepository drawingRepository;

    @Autowired
    GroupService groupService;

    @Autowired
    DailyTopicRepository dailyTopicRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void groupMembersCanSendGeneralQuoteAndReplyMessages() {
        User owner = user("chat-owner");
        User friend = user("chat-friend");
        FriendGroup group = groupService.createGroup(owner.getId(), "채팅 방", null);
        groupService.joinGroup(friend.getId(), group.getInviteCode());
        Drawing drawing = drawing(owner, group, "고양이");

        ChatDtos.ChatMessageResponse general = chatService.send(owner.getId(), group.getId(),
                new ChatDtos.SendMessageRequest("그냥 대화", null, null));
        ChatDtos.ChatMessageResponse quoted = chatService.send(friend.getId(), group.getId(),
                new ChatDtos.SendMessageRequest("이 그림 귀엽다", drawing.getId(), null));
        ChatDtos.ChatMessageResponse replied = chatService.send(owner.getId(), group.getId(),
                new ChatDtos.SendMessageRequest("고마워", null, quoted.id()));

        assertThat(general.quote()).isNull();
        assertThat(quoted.quote()).isNotNull();
        assertThat(quoted.quote().drawingId()).isEqualTo(drawing.getId());
        assertThat(replied.reply()).isNotNull();
        assertThat(replied.reply().messageId()).isEqualTo(quoted.id());
        assertThat(chatService.messages(owner.getId(), group.getId())).extracting(ChatDtos.ChatMessageResponse::content)
                .containsExactly("그냥 대화", "이 그림 귀엽다", "고마워");
    }

    @Test
    void nonMembersCannotReadMessages() {
        User owner = user("chat-owner-private");
        User outsider = user("chat-outsider");
        FriendGroup group = groupService.createGroup(owner.getId(), "비밀 방", null);

        chatService.send(owner.getId(), group.getId(), new ChatDtos.SendMessageRequest("비밀", null, null));

        assertThatThrownBy(() -> chatService.messages(outsider.getId(), group.getId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deletingLastMemberDeletesMessages() {
        User owner = user("chat-owner-delete");
        FriendGroup group = groupService.createGroup(owner.getId(), "삭제 방", null);
        chatService.send(owner.getId(), group.getId(), new ChatDtos.SendMessageRequest("곧 사라짐", null, null));

        boolean deleted = groupService.leaveGroup(owner.getId(), group.getId());

        assertThat(deleted).isTrue();
        assertThat(chatMessageRepository.findTop100ByGroupIdOrderByCreatedAtAsc(group.getId())).isEmpty();
    }

    private Drawing drawing(User user, FriendGroup group, String topicText) {
        DailyTopic topic = new DailyTopic();
        topic.setGroup(group);
        topic.setDate(LocalDate.now(ZoneId.of("Asia/Seoul")));
        topic.setText(topicText);
        topic.setFromSuggestion(false);
        dailyTopicRepository.save(topic);

        Drawing drawing = new Drawing();
        drawing.setUser(user);
        drawing.setGroup(group);
        drawing.setTopic(topic);
        drawing.setImageUrl("/uploads/test.webp");
        return drawingRepository.save(drawing);
    }

    private User user(String prefix) {
        User user = new User();
        user.setUsername(prefix + System.nanoTime());
        user.setEmail(prefix + System.nanoTime() + "@example.com");
        user.setPasswordHash("password");
        return userRepository.save(user);
    }
}
