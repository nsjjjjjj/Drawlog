package com.drawlog.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {
    public record SignupRequest(
            @NotBlank @Size(min = 2, max = 40) String nickname,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 80) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record AuthResponse(String token, Long userId, String nickname, String email, String profileImageUrl) {}
    public record MeResponse(Long id, String email, String nickname, String profileImageUrl) {}
}
