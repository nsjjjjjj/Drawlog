package com.drawlog.user;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/api/users/me/nickname")
    public UserDtos.UserResponse updateNickname(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody UserDtos.NicknameRequest request
    ) {
        return userService.updateNickname(user.id(), request.nickname());
    }

    @PatchMapping("/api/users/me/profile-image")
    public UserDtos.UserResponse updateProfileImage(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam("image") MultipartFile image
    ) {
        return userService.updateProfileImage(user.id(), image);
    }

    @DeleteMapping("/api/users/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal CurrentUser user) {
        userService.deleteMe(user.id());
    }
}
