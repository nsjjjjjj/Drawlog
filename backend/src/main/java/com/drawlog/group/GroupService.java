package com.drawlog.group;

import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.config.AppProperties;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final FriendGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final DailyTopicRepository dailyTopicRepository;
    private final AppProperties properties;

    public GroupService(FriendGroupRepository groupRepository, GroupMemberRepository memberRepository, UserRepository userRepository,
                        DailyTopicRepository dailyTopicRepository, AppProperties properties) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.dailyTopicRepository = dailyTopicRepository;
        this.properties = properties;
    }

    @Transactional
    public FriendGroup createGroup(Long userId, String name, int maxMembers, String initialTopic) {
        if (maxMembers < 2 || maxMembers > 12) throw new ApiException(ErrorCode.BAD_REQUEST, "그룹 최대 인원은 2~12명입니다.");
        User owner = user(userId);
        FriendGroup group = new FriendGroup();
        group.setName(name);
        group.setMaxMembers(maxMembers);
        group.setInviteCode(newInviteCode());
        groupRepository.save(group);
        addMember(group, owner, MemberRole.OWNER);
        if (initialTopic != null && !initialTopic.isBlank()) {
            DailyTopic topic = new DailyTopic();
            topic.setGroup(group);
            topic.setTopicDate(LocalDate.now(ZoneId.of(properties.getTimeZone())));
            topic.setText(initialTopic.trim());
            dailyTopicRepository.save(topic);
        }
        return group;
    }

    @Transactional
    public FriendGroup joinGroup(Long userId, String inviteCode) {
        User user = user(userId);
        FriendGroup group = groupRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "초대코드를 찾을 수 없습니다."));
        if (memberRepository.existsByGroupIdAndUserId(group.getId(), userId)) return group;
        if (memberRepository.countByGroupId(group.getId()) >= group.getMaxMembers()) throw new ApiException(ErrorCode.GROUP_FULL);
        addMember(group, user, MemberRole.MEMBER);
        return group;
    }

    @Transactional(readOnly = true)
    public FriendGroup requireGroup(Long userId, Long groupId) {
        FriendGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "그룹을 찾을 수 없습니다."));
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) throw new ApiException(ErrorCode.NOT_GROUP_MEMBER);
        return group;
    }

    @Transactional(readOnly = true)
    public GroupMember requireMembership(Long userId, Long groupId) {
        return memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_GROUP_MEMBER));
    }

    @Transactional(readOnly = true)
    public FriendGroup requireOwner(Long userId, Long groupId) {
        GroupMember member = requireMembership(userId, groupId);
        if (member.getRole() != MemberRole.OWNER) throw new ApiException(ErrorCode.FORBIDDEN, "방장만 할 수 있습니다.");
        return member.getGroup();
    }

    @Transactional(readOnly = true)
    public List<FriendGroup> groupsForUser(Long userId) {
        return memberRepository.findByUserIdOrderByJoinedAtAsc(userId).stream().map(GroupMember::getGroup).toList();
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.GroupDetailResponse> groupDetailsForUser(Long userId) {
        List<FriendGroup> groups = groupsForUser(userId);
        if (groups.isEmpty()) return List.of();
        List<Long> groupIds = groups.stream().map(FriendGroup::getId).toList();
        Map<Long, List<GroupDtos.MemberResponse>> membersByGroup = memberRepository.findMembersForGroups(groupIds).stream()
                .collect(Collectors.groupingBy(
                        member -> member.getGroup().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(this::toMemberResponse, Collectors.toList())
                ));
        return groups.stream()
                .map(group -> toDetailResponse(userId, group, membersByGroup.getOrDefault(group.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GroupDtos.MemberResponse> members(Long userId, Long groupId) {
        requireGroup(userId, groupId);
        return memberRepository.findByGroupIdOrderByJoinedAtAsc(groupId).stream()
                .map(this::toMemberResponse)
                .toList();
    }

    public GroupDtos.GroupDetailResponse toDetailResponse(Long userId, FriendGroup group, List<GroupDtos.MemberResponse> members) {
        boolean owner = members.stream().anyMatch(member -> member.userId().equals(userId) && member.role() == MemberRole.OWNER);
        return new GroupDtos.GroupDetailResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                "/invite/" + group.getInviteCode(),
                group.getMaxMembers(),
                owner,
                members
        );
    }

    @Transactional
    public FriendGroup updateGroupName(Long userId, Long groupId, String name) {
        FriendGroup group = requireOwner(userId, groupId);
        group.setName(name);
        if (maxMembers != null) {
            long currentMembers = memberRepository.countByGroupId(groupId);
            if (maxMembers < currentMembers) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "현재 멤버 수보다 작은 최대 인원은 선택할 수 없습니다.");
            }
            group.setMaxMembers(maxMembers);
        }
        return group;
    }

    @Transactional
    public void transferOwner(Long ownerId, Long groupId, Long targetUserId) {
        GroupMember owner = requireMembership(ownerId, groupId);
        if (owner.getRole() != MemberRole.OWNER) throw new ApiException(ErrorCode.FORBIDDEN, "방장만 할 수 있습니다.");
        GroupMember target = memberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_GROUP_MEMBER));
        owner.setRole(MemberRole.MEMBER);
        target.setRole(MemberRole.OWNER);
    }

    @Transactional
    public void leave(Long userId, Long groupId) {
        GroupMember member = requireMembership(userId, groupId);
        if (member.getRole() == MemberRole.OWNER && memberRepository.countByGroupId(groupId) > 1) {
            throw new ApiException(ErrorCode.OWNER_TRANSFER_REQUIRED);
        }
        memberRepository.delete(member);
    }

    @Transactional
    public void removeMember(Long ownerId, Long groupId, Long targetUserId) {
        requireOwner(ownerId, groupId);
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_GROUP_MEMBER));
        if (member.getRole() == MemberRole.OWNER) throw new ApiException(ErrorCode.FORBIDDEN, "방장은 내보낼 수 없습니다.");
        memberRepository.delete(member);
    }

    private void addMember(FriendGroup group, User user, MemberRole role) {
        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUser(user);
        member.setRole(role);
        memberRepository.save(member);
    }

    private GroupDtos.MemberResponse toMemberResponse(GroupMember member) {
        return new GroupDtos.MemberResponse(
                member.getUser().getId(),
                member.getUser().getNickname(),
                member.getUser().getProfileImageUrl(),
                member.getRole(),
                member.getJoinedAt()
        );
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private String newInviteCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            code = builder.toString();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }
}
