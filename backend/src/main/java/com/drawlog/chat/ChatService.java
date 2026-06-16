package com.drawlog.chat;

import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.GroupService;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;
    private final DrawingRepository drawingRepository;
    private final GroupService groupService;
    private final UserRepository userRepository;

    public ChatService(ChatMessageRepository chatMessageRepository, DrawingRepository drawingRepository,
                       GroupService groupService, UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.drawingRepository = drawingRepository;
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatDtos.ChatMessageResponse> messages(Long userId, Long groupId) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        int pageSize = Math.min(Math.max(size, 1), 50);
        List<ChatMessage> rows = (cursor == null
                ? chatMessageRepository.findByGroupIdAndDeletedAtIsNullOrderByCreatedAtDesc(group.getId(), PageRequest.of(0, pageSize))
                : chatMessageRepository.findByGroupIdAndDeletedAtIsNullAndIdLessThanOrderByCreatedAtDesc(group.getId(), cursor, PageRequest.of(0, pageSize)));
        List<ChatDtos.ChatMessageResponse> messages = rows.stream()
                .sorted(Comparator.comparing(ChatMessage::getCreatedAt).thenComparing(ChatMessage::getId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ChatDtos.ChatMessageResponse send(Long userId, Long groupId, ChatDtos.SendMessageRequest request) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

        ChatMessage message = new ChatMessage();
        message.setGroup(group);
        message.setUser(user);
        message.setContent(request.content().trim());

        if (request.drawingId() != null && request.replyToMessageId() != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "한 번에 하나만 인용할 수 있습니다.");
        }

        if (request.type() == ChatMessageType.DRAWING_QUOTE) {
            if (request.drawingId() == null) throw new ApiException(ErrorCode.BAD_REQUEST, "인용할 그림이 필요합니다.");
            Drawing drawing = drawingRepository.findById(request.drawingId())
                    .orElseThrow(() -> new ApiException(ErrorCode.DRAWING_NOT_FOUND));
            if (!drawing.getGroup().getId().equals(group.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "같은 그룹의 그림만 인용할 수 있습니다.");
            }
            message.setQuotedDrawingId(drawing.getId());
            message.setQuotedDrawingImageUrl(drawing.getImageUrl());
            message.setQuotedDrawingAuthor(drawing.getUser().getUsername());
            message.setQuotedDrawingTopicText(drawing.getTopic().getText());
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

        return toResponse(chatMessageRepository.save(message));
    }

    public ChatDtos.ChatMessageResponse toResponse(ChatMessage message) {
        ChatDtos.QuoteResponse quote = message.getQuotedDrawingId() == null ? null : new ChatDtos.QuoteResponse(
                message.getQuotedDrawingId(),
                message.getQuotedDrawingImageUrl(),
                message.getQuotedDrawingAuthor(),
                message.getQuotedDrawingTopicText()
        );
        ChatDtos.ReplyResponse reply = message.getReplyToMessageId() == null ? null : new ChatDtos.ReplyResponse(
                message.getReplyToMessageId(),
                message.getReplyToUsername(),
                message.getReplyToContent()
        );
        ChatDtos.ReplyToMessageResponse replyTo = null;
        ChatMessage replyToMessage = message.getReplyToMessage();
        if (replyToMessage != null && replyToMessage.getDeletedAt() == null) {
            replyTo = new ChatDtos.ReplyToMessageResponse(
                    replyToMessage.getId(),
                    replyToMessage.getSender().getId(),
                    replyToMessage.getSender().getNickname(),
                    replyToMessage.getContent(),
                    replyToMessage.getCreatedAt()
            );
        }
        return new ChatDtos.ChatMessageResponse(
                message.getId(),
                message.getGroup().getId(),
                message.getSender().getId(),
                message.getSender().getNickname(),
                message.getSender().getProfileImageUrl(),
                message.getType(),
                message.getContent(),
                drawing == null ? null : drawing.getId(),
                message.getDeletedAt(),
                message.getCreatedAt(),
                quote,
                replyTo
        );
    }

    private String preview(String content) {
        return content.length() <= 220 ? content : content.substring(0, 217) + "...";
    }
}
