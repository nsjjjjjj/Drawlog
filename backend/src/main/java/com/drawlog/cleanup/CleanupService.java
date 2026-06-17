package com.drawlog.cleanup;

import com.drawlog.chat.ChatMessageRepository;
import com.drawlog.config.AppProperties;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.FriendGroupRepository;
import com.drawlog.group.GroupMember;
import com.drawlog.group.GroupMemberRepository;
import com.drawlog.group.MemberRole;
import com.drawlog.notification.GroupNotificationSettingsRepository;
import com.drawlog.notification.NotificationRepository;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.topic.TopicSuggestionRepository;
import com.drawlog.topic.TopicVoteRepository;
import com.drawlog.user.UserRepository;
import com.drawlog.user.UserStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CleanupService {
    private static final Logger log = LoggerFactory.getLogger(CleanupService.class);

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final DrawingRepository drawingRepository;
    private final GroupMemberRepository memberRepository;
    private final FriendGroupRepository groupRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NotificationRepository notificationRepository;
    private final GroupNotificationSettingsRepository groupNotificationSettingsRepository;
    private final DailyTopicRepository dailyTopicRepository;
    private final TopicSuggestionRepository topicSuggestionRepository;
    private final TopicVoteRepository topicVoteRepository;

    public CleanupService(AppProperties properties,
                          UserRepository userRepository,
                          DrawingRepository drawingRepository,
                          GroupMemberRepository memberRepository,
                          FriendGroupRepository groupRepository,
                          ChatMessageRepository chatMessageRepository,
                          NotificationRepository notificationRepository,
                          GroupNotificationSettingsRepository groupNotificationSettingsRepository,
                          DailyTopicRepository dailyTopicRepository,
                          TopicSuggestionRepository topicSuggestionRepository,
                          TopicVoteRepository topicVoteRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.drawingRepository = drawingRepository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.notificationRepository = notificationRepository;
        this.groupNotificationSettingsRepository = groupNotificationSettingsRepository;
        this.dailyTopicRepository = dailyTopicRepository;
        this.topicSuggestionRepository = topicSuggestionRepository;
        this.topicVoteRepository = topicVoteRepository;
    }

    @Transactional
    public CleanupReport cleanup(boolean apply) {
        MutableReport report = new MutableReport(apply);
        cleanupGroupsAndStaleMemberships(apply, report);
        cleanupDeletedUserDrawings(apply, report);
        cleanupDeletedUserGroupSettings(apply);
        cleanupOrphanUploads(apply, report);
        return report.toReport();
    }

    private void cleanupGroupsAndStaleMemberships(boolean apply, MutableReport report) {
        for (FriendGroup group : groupRepository.findAll()) {
            List<GroupMember> memberships = memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId());
            if (memberships.isEmpty()) {
                report.deletedEmptyGroups++;
                if (apply) deleteGroup(group, report);
                continue;
            }

            List<GroupMember> activeMembers = memberships.stream()
                    .filter(member -> member.getUser().getStatus() == UserStatus.ACTIVE)
                    .toList();
            List<GroupMember> deletedMembers = memberships.stream()
                    .filter(member -> member.getUser().getStatus() == UserStatus.DELETED)
                    .toList();

            if (activeMembers.isEmpty()) {
                report.deletedMemberships += deletedMembers.size();
                report.deletedEmptyGroups++;
                if (apply) deleteGroup(group, report);
                continue;
            }

            boolean hasActiveOwner = activeMembers.stream().anyMatch(member -> member.getRole() == MemberRole.OWNER);
            if (!hasActiveOwner) {
                report.ownerRepairedGroups++;
                if (apply) activeMembers.get(0).setRole(MemberRole.OWNER);
            }

            if (!deletedMembers.isEmpty()) {
                report.deletedMemberships += deletedMembers.size();
                if (apply) memberRepository.deleteAll(deletedMembers);
            }
        }
        if (apply) memberRepository.flush();
    }

    private void cleanupDeletedUserDrawings(boolean apply, MutableReport report) {
        List<Drawing> drawings = drawingRepository.findByUserStatus(UserStatus.DELETED);
        if (drawings.isEmpty()) return;
        report.deletedDrawings += drawings.size();
        List<Long> drawingIds = drawings.stream().map(Drawing::getId).toList();
        if (apply) {
            clearDrawingReferences(drawingIds);
            for (Drawing drawing : drawings) {
                if (deleteUploadReference(drawing.getImagePath())) report.deletedDrawingFiles++;
            }
            drawingRepository.deleteAll(drawings);
        }
    }

    private void cleanupDeletedUserGroupSettings(boolean apply) {
        if (!apply) return;
        userRepository.findAll().stream()
                .filter(user -> user.getStatus() == UserStatus.DELETED)
                .forEach(user -> groupNotificationSettingsRepository.deleteByUserId(user.getId()));
    }

    private void cleanupOrphanUploads(boolean apply, MutableReport report) {
        Set<String> referencedUploads = referencedUploads();
        for (String uploadFile : uploadFiles()) {
            if (referencedUploads.contains(uploadFile)) continue;
            report.orphanUploadCandidates++;
            if (apply && deleteUploadFile(uploadFile)) {
                report.deletedUploadFiles++;
            }
        }
    }

    private Set<String> referencedUploads() {
        Set<String> references = new HashSet<>();
        userRepository.findAll().forEach(user -> addUploadReference(references, user.getProfileImageUrl()));
        drawingRepository.findAll().forEach(drawing -> addUploadReference(references, drawing.getImagePath()));
        return references;
    }

    private void addUploadReference(Set<String> references, String imageUrl) {
        String relative = relativeUploadPath(imageUrl);
        if (relative != null) references.add(relative);
    }

    private List<String> uploadFiles() {
        Path uploadDir = Path.of(properties.getUploadDir()).normalize();
        if (!Files.exists(uploadDir)) return List.of();
        try (var paths = Files.walk(uploadDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> uploadDir.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Failed to scan uploads directory. uploadDir={}", uploadDir, e);
            return List.of();
        }
    }

    private void deleteGroup(FriendGroup group, MutableReport report) {
        Long groupId = group.getId();
        deleteGroupDrawings(groupId, report);
        chatMessageRepository.clearReplyReferencesForGroup(groupId);
        chatMessageRepository.deleteByGroupId(groupId);
        notificationRepository.deleteByGroupId(groupId);
        groupNotificationSettingsRepository.deleteByGroupId(groupId);
        dailyTopicRepository.clearSelectedSuggestionsForGroup(groupId);
        dailyTopicRepository.deleteByGroupId(groupId);
        topicVoteRepository.deleteByGroupId(groupId);
        topicSuggestionRepository.deleteByGroupId(groupId);
        memberRepository.deleteByGroupId(groupId);
        groupRepository.delete(group);
    }

    private void deleteGroupDrawings(Long groupId, MutableReport report) {
        List<Drawing> drawings = drawingRepository.findByGroupId(groupId);
        if (drawings.isEmpty()) return;
        report.deletedGroupDrawings += drawings.size();
        report.deletedDrawings += (int) drawings.stream()
                .filter(drawing -> drawing.getUser().getStatus() == UserStatus.DELETED)
                .count();
        List<Long> drawingIds = drawings.stream().map(Drawing::getId).toList();
        clearDrawingReferences(drawingIds);
        drawings.forEach(drawing -> {
            boolean deleted = deleteUploadReference(drawing.getImagePath());
            if (deleted && drawing.getUser().getStatus() == UserStatus.DELETED) {
                report.deletedDrawingFiles++;
            }
        });
        drawingRepository.deleteAll(drawings);
    }

    private void clearDrawingReferences(List<Long> drawingIds) {
        if (drawingIds.isEmpty()) return;
        chatMessageRepository.findByDrawingIdIn(drawingIds).forEach(message -> message.setDrawing(null));
        chatMessageRepository.clearDrawingReferences(drawingIds);
    }

    private boolean deleteUploadReference(String imageUrl) {
        String relative = relativeUploadPath(imageUrl);
        return relative != null && deleteUploadFile(relative);
    }

    private boolean deleteUploadFile(String relativePath) {
        Path uploadDir = Path.of(properties.getUploadDir()).normalize();
        Path target = uploadDir.resolve(relativePath).normalize();
        if (!target.startsWith(uploadDir)) {
            log.warn("Skipped suspicious upload delete target. relativePath={}, target={}", relativePath, target);
            return false;
        }
        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete cleanup upload file. relativePath={}, target={}", relativePath, target, e);
            return false;
        }
    }

    private String relativeUploadPath(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String prefix = properties.getPublicUploadPath() + "/";
        if (!imageUrl.startsWith(prefix)) return null;
        String relative = imageUrl.substring(prefix.length()).replace('\\', '/');
        if (relative.isBlank() || relative.contains("..")) return null;
        return relative;
    }

    private static class MutableReport {
        private final boolean apply;
        private int orphanUploadCandidates;
        private int deletedUploadFiles;
        private int deletedMemberships;
        private int deletedDrawings;
        private int deletedDrawingFiles;
        private int ownerRepairedGroups;
        private int deletedEmptyGroups;
        private int deletedGroupDrawings;

        private MutableReport(boolean apply) {
            this.apply = apply;
        }

        private CleanupReport toReport() {
            return new CleanupReport(
                    apply,
                    orphanUploadCandidates,
                    deletedUploadFiles,
                    deletedMemberships,
                    deletedDrawings,
                    deletedDrawingFiles,
                    ownerRepairedGroups,
                    deletedEmptyGroups,
                    deletedGroupDrawings
            );
        }
    }
}
