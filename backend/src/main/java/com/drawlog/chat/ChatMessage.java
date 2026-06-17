package com.drawlog.chat;

import com.drawlog.drawing.Drawing;
import com.drawlog.group.FriendGroup;
import com.drawlog.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_group_created_at", columnList = "group_id,created_at"),
        @Index(name = "idx_chat_messages_group_deleted_id", columnList = "group_id,deleted_at,id")
})
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private FriendGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatMessageType type = ChatMessageType.TEXT;

    @Column(nullable = false, length = 1000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drawing_id")
    private Drawing drawing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id")
    private ChatMessage replyToMessage;

    private Instant deletedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public FriendGroup getGroup() { return group; }
    public void setGroup(FriendGroup group) { this.group = group; }
    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }
    public ChatMessageType getType() { return type; }
    public void setType(ChatMessageType type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Drawing getDrawing() { return drawing; }
    public void setDrawing(Drawing drawing) { this.drawing = drawing; }
    public ChatMessage getReplyToMessage() { return replyToMessage; }
    public void setReplyToMessage(ChatMessage replyToMessage) { this.replyToMessage = replyToMessage; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
