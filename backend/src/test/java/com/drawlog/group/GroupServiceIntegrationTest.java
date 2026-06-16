package com.drawlog.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupServiceIntegrationTest {
    @Autowired
    GroupService groupService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    FriendGroupRepository groupRepository;

    @Autowired
    GroupMemberRepository memberRepository;

    @Autowired
    DailyTopicRepository dailyTopicRepository;

    @Test
    void ownerCanCreateGroupWithInitialTopicAndManageMembers() {
        User owner = user("owner");
        User friend = user("friend");

        FriendGroup group = groupService.createGroup(owner.getId(), "첫 방", "처음 그릴 주제");
        groupService.joinGroup(friend.getId(), group.getInviteCode());

        assertThat(group.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(memberRepository.countByGroupId(group.getId())).isEqualTo(2);
        assertThat(dailyTopicRepository.findByGroupIdAndDate(group.getId(), LocalDate.now(ZoneId.of("Asia/Seoul"))))
                .get()
                .extracting("text")
                .isEqualTo("처음 그릴 주제");

        FriendGroup renamed = groupService.updateGroupName(owner.getId(), group.getId(), "바뀐 방");
        assertThat(renamed.getName()).isEqualTo("바뀐 방");

        String oldInvite = group.getInviteCode();
        FriendGroup regenerated = groupService.regenerateInviteCode(owner.getId(), group.getId());
        assertThat(regenerated.getInviteCode()).isNotEqualTo(oldInvite);

        assertThatThrownBy(() -> groupService.regenerateInviteCode(friend.getId(), group.getId()))
                .isInstanceOf(ResponseStatusException.class);

        groupService.removeMember(owner.getId(), group.getId(), friend.getId());
        assertThat(memberRepository.existsByGroupIdAndUserId(group.getId(), friend.getId())).isFalse();
    }

    @Test
    void ownerLeavingTransfersOwnershipAndLastMemberDeletesGroup() {
        User owner = user("transfer-owner");
        User friend = user("transfer-friend");
        FriendGroup group = groupService.createGroup(owner.getId(), "승계 방", null);
        groupService.joinGroup(friend.getId(), group.getInviteCode());

        boolean deletedAfterOwnerLeave = groupService.leaveGroup(owner.getId(), group.getId());
        assertThat(deletedAfterOwnerLeave).isFalse();
        assertThat(groupRepository.findById(group.getId())).get().extracting("owner.id").isEqualTo(friend.getId());

        boolean deletedAfterLastLeave = groupService.leaveGroup(friend.getId(), group.getId());
        assertThat(deletedAfterLastLeave).isTrue();
        assertThat(groupRepository.findById(group.getId())).isEmpty();
        assertThat(memberRepository.countByGroupId(group.getId())).isZero();
    }

    private User user(String prefix) {
        User user = new User();
        user.setUsername(prefix + System.nanoTime());
        user.setEmail(prefix + System.nanoTime() + "@example.com");
        user.setPasswordHash("password");
        return userRepository.save(user);
    }
}
