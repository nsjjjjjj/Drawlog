package com.drawlog.chat;

import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.GroupService;
import com.drawlog.notification.NotificationService;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import com.drawlog.user.UserStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final DrawingRepository drawingRepository;
    private final GroupService groupService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ChatService(ChatMessageRepository chatMessageRepository, DrawingRepository drawingRepository,
                       GroupService groupService, UserRepository userRepository, NotificationService notificationService) {
        this.chatMessageRepository = chatMessageRepository;
        this.drawingRepository = drawingRepository;
        this.groupService = groupService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ChatDtos.ChatPageResponse messages(Long userId, Long groupId, Long cursor, int size) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        int pageSize = Math.min(Math.max(size, 1), 50);
        List<ChatMessage> rows = (cursor == null
                ? chatMessageRepository.findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(group.getId(), PageRequest.of(0, pageSize))
                : chatMessageRepository.findByGroupIdAndDeletedAtIsNullAndIdLessThanOrderByCreatedAtDesc(group.getId(), cursor, PageRequest.of(0, pageSize)));
        List<ChatDtos.ChatMessageResponse> messages = rows.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId))
                .map(this::toResponse)
                .toList();
        Long nextCursor = rows.size() < pageSize ? null : rows.get(rows.size() - 1).getId();
        return new ChatDtos.ChatPageResponse(messages, nextCursor);
    }

    @Transactional
    public ChatDtos.ChatMessageResponse send(Long userId, Long groupId, ChatDtos.SendMessageRequest request) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        ChatMessage message = new ChatMessage();
        message.setGroup(group);
        message.setSender(user);
        message.setType(request.type());
        message.setContent(request.content().trim());

        if (request.drawingId() != null && request.replyToMessageId() != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "한 번에 하나만 인용할 수 있습니다.");
        }

        if (request.type() == ChatMessageType.DRAWING_QUOTE) {
            if (request.drawingId() == null) throw new ApiException(ErrorCode.BAD_REQUEST, "인용할 그림이 필요합니다.");
            Drawing drawing = drawingRepository.findById(request.drawingId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DRAWING_NOT_FOUND));
            if (!drawing.getGroup().getId().equals(group.getId())) {
                throw new ApiException(ErrorCode.FORBIDDEN, "같은 그룹의 그림만 인용할 수 있습니다.");
            }
            message.setDrawing(drawing);
        }

        if (request.replyToMessageId() != null) {
            ChatMessage replyToMessage = chatMessageRepository.findById(request.replyToMessageId())
                    .filter(found -> found.getGroup().getId().equals(group.getId()))
                    .filter(found -> found.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
            message.setReplyToMessage(replyToMessage);
        }

        ChatMessage saved = chatMessageRepository.save(message);
        notificationService.notifyChat(saved);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, Long groupId, Long messageId) {
        groupService.requireGroup(userId, groupId);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .filter(found -> found.getGroup().getId().equals(groupId))
                .orElseThrow(() -> new ApiException(ErrorCode.CHAT_MESSAGE_NOT_FOUND));
        if (!message.getSender().getId().equals(userId)) throw new ApiException(ErrorCode.FORBIDDEN, "내 메시지만 삭제할 수 있습니다.");
        message.setDeletedAt(Instant.now());
    }

    public ChatDtos.ChatMessageResponse toResponse(ChatMessage message) {
        Drawing drawing = message.getDrawing();
        String imageUrl = drawing == null ? null : drawing.getImagePath();
        ChatDtos.QuoteResponse quote = drawing == null ? null : new ChatDtos.QuoteResponse(
                drawing.getId(),
                imageUrl,
                imageUrl,
                imageUrl,
                username(drawing.getUser()),
                drawing.getDailyTopic().getText()
        );
        ChatDtos.ReplyToMessageResponse replyTo = null;
        ChatMessage replyToMessage = message.getReplyToMessage();
        if (replyToMessage != null && replyToMessage.getDeletedAt() == null) {
            User replySender = replyToMessage.getSender();
            replyTo = new ChatDtos.ReplyToMessageResponse(
                    replyToMessage.getId(),
                    replySender == null ? null : replySender.getId(),
                    username(replySender),
                    replyToMessage.getContent(),
                    replyToMessage.getCreatedAt()
            );
        }
        User sender = message.getSender();
        return new ChatDtos.ChatMessageResponse(
                message.getId(),
                message.getGroup().getId(),
                sender == null ? null : sender.getId(),
                username(sender),
                profileImageUrl(sender),
                message.getType(),
                message.getContent(),
                drawing == null ? null : drawing.getId(),
                message.getDeletedAt(),
                message.getCreatedAt(),
                quote,
                replyTo
        );
    }

    private String username(User user) {
        return user == null || user.getStatus() == UserStatus.DELETED ? "알 수 없는 사용자" : user.getNickname();
    }

    private String profileImageUrl(User user) {
        return user == null || user.getStatus() == UserStatus.DELETED ? null : user.getProfileImageUrl();
    }
}
