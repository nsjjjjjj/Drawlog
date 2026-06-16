package com.drawlog.group;

import com.drawlog.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public GroupDtos.GroupDetailResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody GroupDtos.CreateGroupRequest request) {
        FriendGroup group = groupService.createGroup(user.id(), request.name(), request.maxMembers(), request.initialTopic());
        return detail(user.id(), group);
    }

    @GetMapping
    public List<GroupDtos.GroupDetailResponse> list(@AuthenticationPrincipal CurrentUser user) {
        return groupService.groupDetailsForUser(user.id());
    }

    @GetMapping("/{groupId}")
    public GroupDtos.GroupDetailResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId) {
        return detail(user.id(), groupService.requireGroup(user.id(), groupId));
    }

    @PatchMapping("/{groupId}")
    public GroupDtos.GroupDetailResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @Valid @RequestBody GroupDtos.UpdateGroupRequest request) {
        return detail(user.id(), groupService.updateGroupName(user.id(), groupId, request.name()));
    }

    @PostMapping("/join")
    public GroupDtos.GroupDetailResponse join(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody GroupDtos.JoinGroupRequest request) {
        return detail(user.id(), groupService.joinGroup(user.id(), request.inviteCode()));
    }

    @PostMapping("/{groupId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId) {
        groupService.leave(user.id(), groupId);
    }

    @PostMapping("/{groupId}/transfer-owner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transferOwner(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @Valid @RequestBody GroupDtos.TransferOwnerRequest request) {
        groupService.transferOwner(user.id(), groupId, request.targetUserId());
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal CurrentUser user, @PathVariable Long groupId, @PathVariable Long userId) {
        groupService.removeMember(user.id(), groupId, userId);
    }

    private GroupDtos.GroupDetailResponse detail(Long userId, FriendGroup group) {
        return groupService.toDetailResponse(userId, group, groupService.members(userId, group.getId()));
    }
}
