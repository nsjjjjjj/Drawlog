package com.drawlog.group;

import com.drawlog.drawing.DrawingRepository;
import com.drawlog.chat.ChatMessageRepository;
import com.drawlog.config.AppProperties;
import com.drawlog.storage.StorageService;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.topic.TopicSuggestionRepository;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GroupService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final FriendGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DrawingRepository drawingRepository;
    private final TopicSuggestionRepository suggestionRepository;
    private final DailyTopicRepository dailyTopicRepository;
    private final StorageService storageService;
    private final AppProperties properties;

    public GroupService(FriendGroupRepository groupRepository, GroupMemberRepository memberRepository, UserRepository userRepository,
                        ChatMessageRepository chatMessageRepository, DrawingRepository drawingRepository, TopicSuggestionRepository suggestionRepository,
                        DailyTopicRepository dailyTopicRepository, StorageService storageService, AppProperties properties) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.drawingRepository = drawingRepository;
        this.suggestionRepository = suggestionRepository;
        this.dailyTopicRepository = dailyTopicRepository;
        this.storageService = storageService;
        this.properties = properties;
    }

    @Transactional
    public FriendGroup createGroup(Long userId, String name, String initialTopic) {
        User owner = getUser(userId);
        FriendGroup group = new FriendGroup();
        group.setName(name);
        group.setOwner(owner);
        group.setInviteCode(newInviteCode());
        groupRepository.save(group);
        addMember(group, owner);
        if (initialTopic != null && !initialTopic.isBlank()) {
            DailyTopic topic = new DailyTopic();
            topic.setGroup(group);
            topic.setDate(LocalDate.now(zone()));
            topic.setText(initialTopic.trim());
            topic.setFromSuggestion(false);
            dailyTopicRepository.save(topic);
        }
        return group;
    }

    @Transactional
    public FriendGroup joinGroup(Long userId, String inviteCode) {
        User user = getUser(userId);
        FriendGroup group = groupRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "초대코드를 찾을 수 없습니다."));
        if (!memberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            addMember(group, user);
        }
        return group;
    }

    public FriendGroup requireActiveGroup(Long userId) {
        return memberRepository.findFirstByUserIdOrderByJoinedAtAsc(userId)
                .map(GroupMember::getGroup)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "먼저 그룹을 만들거나 초대코드로 입장하세요."));
    }

    public FriendGroup requireGroup(Long userId, Long groupId) {
        if (groupId == null) {
            return requireActiveGroup(userId);
        }
        FriendGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그룹을 찾을 수 없습니다."));
        if (!isMember(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 그룹에 속한 사용자만 접근할 수 있습니다.");
        }
        return group;
    }

    @Transactional(readOnly = true)
    public java.util.List<FriendGroup> groupsForUser(Long userId) {
        return memberRepository.findByUserIdOrderByJoinedAtAsc(userId)
                .stream()
                .map(GroupMember::getGroup)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<GroupDtos.MemberResponse> members(Long userId, Long groupId) {
        FriendGroup group = requireGroup(userId, groupId);
        return memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId())
                .stream()
                .map(member -> new GroupDtos.MemberResponse(
                        member.getUser().getId(),
                        member.getUser().getUsername(),
                        group.getOwner().getId().equals(member.getUser().getId())
                ))
                .toList();
    }

    public boolean isMember(Long groupId, Long userId) {
        return memberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Transactional
    public boolean leaveGroup(Long userId, Long groupId) {
        FriendGroup group = requireGroup(userId, groupId);
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그룹 멤버가 아닙니다."));
        memberRepository.delete(member);
        memberRepository.flush();
        if (memberRepository.countByGroupId(groupId) == 0) {
            deleteEmptyGroup(groupId);
            return true;
        }
        if (group.getOwner().getId().equals(userId)) {
            GroupMember nextOwner = memberRepository.findByGroupIdOrderByJoinedAtAsc(groupId).get(0);
            group.setOwner(nextOwner.getUser());
        }
        return false;
    }

    @Transactional
    public FriendGroup updateGroup(Long userId, Long groupId, String name, Integer maxMembers) {
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
    public FriendGroup regenerateInviteCode(Long userId, Long groupId) {
        FriendGroup group = requireOwnedGroup(userId, groupId);
        group.setInviteCode(newInviteCode());
        return group;
    }

    @Transactional
    public void removeMember(Long ownerId, Long groupId, Long targetUserId) {
        FriendGroup group = requireOwnedGroup(ownerId, groupId);
        if (group.getOwner().getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "방장은 내보낼 수 없습니다. 먼저 그룹에서 직접 나가세요.");
        }
        GroupMember member = memberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그룹 멤버를 찾을 수 없습니다."));
        memberRepository.delete(member);
    }

    private FriendGroup requireOwnedGroup(Long userId, Long groupId) {
        FriendGroup group = requireGroup(userId, groupId);
        if (!group.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "그룹장만 할 수 있습니다.");
        }
        return group;
    }

    private void deleteEmptyGroup(Long groupId) {
        chatMessageRepository.deleteByGroupId(groupId);
        drawingRepository.findByGroupId(groupId).forEach(drawing -> {
            storageService.deleteImage(drawing.getImageUrl());
            drawingRepository.delete(drawing);
        });
        suggestionRepository.deleteByGroupId(groupId);
        dailyTopicRepository.deleteByGroupId(groupId);
        memberRepository.deleteByGroupId(groupId);
        groupRepository.deleteById(groupId);
    }

    private void addMember(FriendGroup group, User user) {
        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUser(user);
        memberRepository.save(member);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }

    private String newInviteCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            code = builder.toString();
        } while (groupRepository.existsByInviteCode(code));
        return code;
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTimeZone());
    }
}
