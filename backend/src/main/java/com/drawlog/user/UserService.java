package com.drawlog.user;

import com.drawlog.auth.RefreshTokenRepository;
import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.storage.StorageService;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(UserRepository userRepository, StorageService storageService, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public UserDtos.UserResponse updateNickname(Long userId, String nickname) {
        User user = user(userId);
        user.setNickname(nickname.trim());
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse updateProfileImage(Long userId, MultipartFile image) {
        User user = user(userId);
        String oldImage = user.getProfileImageUrl();
        user.setProfileImageUrl(storageService.storeImage(image).imageUrl());
        if (oldImage != null) storageService.deleteImage(oldImage);
        return toResponse(user);
    }

    @Transactional
    public UserDtos.UserResponse deleteProfileImage(Long userId) {
        User user = user(userId);
        String oldImage = user.getProfileImageUrl();
        user.setProfileImageUrl(null);
        if (oldImage != null) storageService.deleteImage(oldImage);
        return toResponse(user);
    }

    @Transactional
    public void deleteMe(Long userId) {
        User user = user(userId);
        String oldImage = user.getProfileImageUrl();
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());
        user.setEmail("deleted_user_" + user.getId() + "@drawlog.local");
        user.setNickname("탈퇴한 사용자");
        user.setProfileImageUrl(null);
        if (oldImage != null) storageService.deleteImage(oldImage);
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(token -> token.setRevokedAt(Instant.now()));
    }

    public UserDtos.UserResponse toResponse(User user) {
        return new UserDtos.UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getStatus()
        );
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
