package com.drawlog.group;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDtos.GroupResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody GroupDtos.CreateGroupRequest request) {
        return toResponse(groupService.createGroup(user.id(), request.name(), request.initialTopic()));
    }

    @PostMapping("/join")
    public GroupDtos.GroupResponse join(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody GroupDtos.JoinGroupRequest request) {
        return toResponse(groupService.joinGroup(user.id(), request.inviteCode()));
    }

    @GetMapping
    public List<GroupDtos.GroupDetailResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return groupService.groupsForUser(user.id()).stream()
                .map(group -> new GroupDtos.GroupDetailResponse(
                        group.getId(),
                        group.getName(),
                        group.getInviteCode(),
                        group.getOwner().getId(),
                        group.getOwner().getId().equals(user.id()),
                        groupService.members(user.id(), group.getId())
                ))
                .toList();
    }

    @PatchMapping("/{id}")
    public GroupDtos.GroupResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id, @Valid @RequestBody GroupDtos.UpdateGroupRequest request) {
        return toResponse(groupService.updateGroupName(user.id(), id, request.name()));
    }

    @PatchMapping("/{groupId}")
    public GroupDtos.GroupDetailResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @Valid @RequestBody GroupDtos.UpdateGroupRequest request) {
        return detail(user.id(), groupService.updateGroup(user.id(), groupId, request.name(), request.maxMembers()));
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id, @PathVariable Long memberUserId) {
        groupService.removeMember(user.id(), id, memberUserId);
    }

    @DeleteMapping("/{id}/membership")
    public GroupDtos.LeaveGroupResponse leave(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return new GroupDtos.LeaveGroupResponse(groupService.leaveGroup(user.id(), id));
    }

    private GroupDtos.GroupResponse toResponse(FriendGroup group) {
        return new GroupDtos.GroupResponse(group.getId(), group.getName(), group.getInviteCode(), group.getOwner().getId());
    }
}
