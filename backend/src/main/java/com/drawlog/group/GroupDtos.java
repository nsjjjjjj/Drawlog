package com.drawlog.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupDtos {
    public record CreateGroupRequest(@NotBlank @Size(min = 2, max = 80) String name, @Size(max = 120) String initialTopic) {}
    public record JoinGroupRequest(@NotBlank String inviteCode) {}
    public record UpdateGroupRequest(
            @NotBlank @Size(min = 2, max = 80) String name,
            @Min(2) @Max(12) Integer maxMembers
    ) {}
    public record TransferOwnerRequest(@NotNull Long targetUserId) {}
    public record DeleteGroupRequest(@NotBlank String confirmName) {}
    public record MemberResponse(Long userId, String nickname, String profileImageUrl, MemberRole role, Instant joinedAt) {}
    public record GroupDetailResponse(Long id, String name, String inviteCode, String inviteLink, int maxMembers, boolean owner, List<MemberResponse> members) {}
}
