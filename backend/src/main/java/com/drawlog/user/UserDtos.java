package com.drawlog.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserDtos {
    public record UserResponse(
            Long id,
            String email,
            String nickname,
            String profileImageUrl,
            UserStatus status
    ) {}

    public record NicknameRequest(@NotBlank @Size(min = 2, max = 80) String nickname) {}
}
