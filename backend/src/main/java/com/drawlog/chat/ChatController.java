package com.drawlog.chat;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/api/groups/{groupId}/messages")
    public List<ChatDtos.ChatMessageResponse> messages(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId
    ) {
        return chatService.messages(user.id(), groupId);
    }

    @PostMapping("/api/groups/{groupId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatDtos.ChatMessageResponse send(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable Long groupId,
            @Valid @RequestBody ChatDtos.SendMessageRequest request
    ) {
        return chatService.send(user.id(), groupId, request);
    }
}
