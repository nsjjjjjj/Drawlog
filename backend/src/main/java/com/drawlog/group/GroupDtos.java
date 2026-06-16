package com.drawlog.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class GroupDtos {
    public record CreateGroupRequest(@NotBlank @Size(min = 2, max = 80) String name, @Size(max = 120) String initialTopic) {}
    public record JoinGroupRequest(@NotBlank String inviteCode) {}
    public record UpdateGroupRequest(@NotBlank @Size(min = 2, max = 80) String name) {}
    public record GroupResponse(Long id, String name, String inviteCode, Long ownerId) {}
    public record MemberResponse(Long userId, String username, boolean owner) {}
    public record GroupDetailResponse(Long id, String name, String inviteCode, Long ownerId, boolean owner, java.util.List<MemberResponse> members) {}
    public record LeaveGroupResponse(boolean groupDeleted) {}
}
