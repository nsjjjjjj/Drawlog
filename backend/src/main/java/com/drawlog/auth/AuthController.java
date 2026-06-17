package com.drawlog.auth;

import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.config.AppProperties;
import com.drawlog.notification.UserNotificationSettings;
import com.drawlog.notification.UserNotificationSettingsRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import com.drawlog.user.UserStatus;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserNotificationSettingsRepository userNotificationSettingsRepository;
    private final AppProperties properties;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                          RefreshTokenRepository refreshTokenRepository,
                          UserNotificationSettingsRepository userNotificationSettingsRepository,
                          AppProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.userNotificationSettingsRepository = userNotificationSettingsRepository;
        this.properties = properties;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.AuthResponse signup(@Valid @RequestBody AuthDtos.SignupRequest request, HttpServletResponse response) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "이미 가입된 이메일입니다.");
        }
        User user = new User();
        user.setNickname(request.nickname());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        UserNotificationSettings settings = new UserNotificationSettings();
        settings.setUser(user);
        userNotificationSettingsRepository.save(settings);
        issueRefreshCookie(user, response);
        return toResponse(user);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.email())
                .filter(found -> found.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
        issueRefreshCookie(user, response);
        return toResponse(user);
    }

    @PostMapping("/refresh")
    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = refreshCookie(request).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        Instant now = Instant.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(raw))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (token.getRevokedAt() != null) {
            revokeActiveTokens(token.getUser(), now);
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        if (!token.getExpiresAt().isAfter(now)) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        token.setRevokedAt(now);
        refreshTokenRepository.save(token);
        issueRefreshCookie(user, response);
        return toResponse(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        refreshCookie(request).flatMap(raw -> refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(hash(raw)))
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(token);
                });
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString());
    }

    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal CurrentUser currentUser) {
        User user = userRepository.findById(currentUser.id()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        return new AuthDtos.MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImageUrl());
    }

    private AuthDtos.AuthResponse toResponse(User user) {
        return new AuthDtos.AuthResponse(
                jwtService.createAccessToken(user.getId(), user.getEmail()),
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getProfileImageUrl()
        );
    }

    private void issueRefreshCookie(User user, HttpServletResponse response) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plusMillis(properties.getRefreshTokenExpirationMs()));
        refreshTokenRepository.save(token);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(raw, properties.getRefreshTokenExpirationMs() / 1000).toString());
    }

    private void revokeActiveTokens(User user, Instant now) {
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(token -> {
            token.setRevokedAt(now);
            refreshTokenRepository.save(token);
        });
    }

    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(properties.getRefreshCookieName(), value)
                .httpOnly(true)
                .secure(properties.isCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }

    private Optional<String> refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(cookie -> properties.getRefreshCookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
