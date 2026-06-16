package com.drawlog.drawing;

import com.drawlog.config.AppProperties;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.GroupService;
import com.drawlog.storage.StorageService;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.TopicService;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DrawingService {
    private final DrawingRepository drawingRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final TopicService topicService;
    private final StorageService storageService;
    private final AppProperties properties;

    public DrawingService(DrawingRepository drawingRepository, UserRepository userRepository, GroupService groupService,
                          TopicService topicService, StorageService storageService, AppProperties properties) {
        this.drawingRepository = drawingRepository;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.topicService = topicService;
        this.storageService = storageService;
        this.properties = properties;
    }

    @Transactional
    public Drawing submit(Long userId, Long groupId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
        FriendGroup group = groupService.requireGroup(userId, groupId);
        DailyTopic topic = topicService.ensureDailyTopic(group, LocalDate.now(zone()));
        String imageUrl = storageService.storeImage(file).imageUrl();

        Drawing drawing = drawingRepository.findByGroupIdAndTopicIdAndUserId(group.getId(), topic.getId(), userId)
                .orElseGet(() -> {
                    Drawing newDrawing = new Drawing();
                    newDrawing.setUser(user);
                    newDrawing.setGroup(group);
                    newDrawing.setTopic(topic);
                    return newDrawing;
                });
        String oldImageUrl = drawing.getImageUrl();
        drawing.setImageUrl(imageUrl);
        Drawing saved = drawingRepository.save(drawing);
        if (oldImageUrl != null) {
            storageService.deleteImage(oldImageUrl);
        }
        return saved;
    }

    @Transactional
    public DrawingDtos.FeedResponse feed(Long userId, Long groupId, LocalDate date) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        DailyTopic topic = topicService.ensureDailyTopic(group, date);
        Instant start = date.atStartOfDay(zone()).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone()).toInstant();
        List<DrawingDtos.DrawingResponse> drawings = drawingRepository
                .findByGroupIdAndCreatedAtBetweenOrderByCreatedAtDesc(group.getId(), start, end)
                .stream()
                .map(this::toResponse)
                .toList();
        java.util.Map<Long, DrawingDtos.DrawingResponse> drawingByUser = drawings.stream()
                .collect(java.util.stream.Collectors.toMap(DrawingDtos.DrawingResponse::userId, drawing -> drawing, (first, second) -> first));
        List<DrawingDtos.MemberDrawingResponse> members = groupService.members(userId, group.getId()).stream()
                .map(member -> new DrawingDtos.MemberDrawingResponse(member.userId(), member.username(), member.owner(), drawingByUser.get(member.userId())))
                .toList();
        return new DrawingDtos.FeedResponse(date, topic.getId(), topic.getText(), drawings, members);
    }

    @Transactional
    public void delete(Long userId, Long drawingId) {
        Drawing drawing = drawingRepository.findById(drawingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "그림을 찾을 수 없습니다."));
        if (!drawing.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "내 그림만 삭제할 수 있습니다.");
        }
        storageService.deleteImage(drawing.getImageUrl());
        drawingRepository.delete(drawing);
    }

    public DrawingDtos.DrawingResponse toResponse(Drawing drawing) {
        return new DrawingDtos.DrawingResponse(
                drawing.getId(),
                drawing.getImageUrl(),
                drawing.getUser().getId(),
                drawing.getUser().getUsername(),
                drawing.getGroup().getId(),
                drawing.getTopic().getId(),
                drawing.getTopic().getText(),
                drawing.getCreatedAt()
        );
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTimeZone());
    }
}
