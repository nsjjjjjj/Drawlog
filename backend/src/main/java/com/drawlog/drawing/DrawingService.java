package com.drawlog.drawing;

import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.config.AppProperties;
import com.drawlog.group.FriendGroup;
import com.drawlog.group.GroupService;
import com.drawlog.notification.NotificationService;
import com.drawlog.storage.StorageService;
import com.drawlog.storage.StoredFile;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.TopicService;
import com.drawlog.user.User;
import com.drawlog.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DrawingService {
    private final DrawingRepository drawingRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final TopicService topicService;
    private final StorageService storageService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public DrawingService(DrawingRepository drawingRepository, UserRepository userRepository, GroupService groupService,
                          TopicService topicService, StorageService storageService, AppProperties properties,
                          ObjectMapper objectMapper, NotificationService notificationService) {
        this.drawingRepository = drawingRepository;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.topicService = topicService;
        this.storageService = storageService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @Transactional
    public Drawing submitToday(Long userId, Long groupId, String strokeJson, MultipartFile thumbnail) {
        User user = user(userId);
        FriendGroup group = groupService.requireGroup(userId, groupId);
        DailyTopic topic = topicService.ensureDailyTopic(userId, groupId, today());
        if (drawingRepository.findByGroupIdAndDailyTopicIdAndUserId(group.getId(), topic.getId(), userId).isPresent()) {
            throw new ApiException(ErrorCode.DRAWING_ALREADY_SUBMITTED);
        }
        Drawing drawing = new Drawing();
        drawing.setUser(user);
        drawing.setGroup(group);
        drawing.setDailyTopic(topic);
        applyPayload(drawing, strokeJson, thumbnail);
        Drawing saved = drawingRepository.save(drawing);
        notificationService.notifyDrawingUploaded(saved);
        return saved;
    }

    @Transactional
    public Drawing updateToday(Long userId, Long groupId, String strokeJson, MultipartFile thumbnail) {
        FriendGroup group = groupService.requireGroup(userId, groupId);
        DailyTopic topic = topicService.ensureDailyTopic(userId, groupId, today());
        Drawing drawing = drawingRepository.findByGroupIdAndDailyTopicIdAndUserId(group.getId(), topic.getId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.DRAWING_NOT_FOUND));
        ensureEditableToday(drawing);
        String oldThumbnail = drawing.getThumbnailPath();
        applyPayload(drawing, strokeJson, thumbnail);
        Drawing saved = drawingRepository.save(drawing);
        if (oldThumbnail != null) storageService.deleteImage(oldThumbnail);
        return saved;
    }

    @Transactional
    public void lockDrawingsBefore(LocalDate date) {
        Instant now = Instant.now();
        drawingRepository.findByDailyTopicTopicDateBeforeAndLockedAtIsNull(date).forEach(drawing -> drawing.setLockedAt(now));
    }

    public DrawingDtos.DrawingResponse toResponse(Drawing drawing) {
        if (drawing == null) return null;
        return new DrawingDtos.DrawingResponse(
                drawing.getId(),
                drawing.getThumbnailPath(),
                drawing.getStrokeData(),
                drawing.getUser().getId(),
                drawing.getUser().getNickname(),
                drawing.getUser().getProfileImageUrl(),
                drawing.getGroup().getId(),
                drawing.getDailyTopic().getId(),
                drawing.getDailyTopic().getText(),
                drawing.getSubmittedAt(),
                drawing.getUpdatedAt(),
                drawing.getLockedAt()
        );
    }

    private void applyPayload(Drawing drawing, String strokeJson, MultipartFile thumbnail) {
        validateStrokeJson(strokeJson);
        StoredFile storedFile = storageService.storeImage(thumbnail);
        drawing.setStrokeData(strokeJson);
        drawing.setThumbnailPath(storedFile.imageUrl());
    }

    private void validateStrokeJson(String strokeJson) {
        try {
            JsonNode root = objectMapper.readTree(strokeJson);
            if (!root.hasNonNull("version") || !root.has("canvas") || !root.has("layers")) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "strokeJson에는 version, canvas, layers가 필요합니다.");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "strokeJson 형식이 올바르지 않습니다.");
        }
    }

    private void ensureEditableToday(Drawing drawing) {
        if (drawing.getLockedAt() != null || !drawing.getDailyTopic().getTopicDate().equals(today())) {
            throw new ApiException(ErrorCode.DRAWING_LOCKED);
        }
    }

    private User user(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    private LocalDate today() {
        return LocalDate.now(zone());
    }

    private ZoneId zone() {
        return ZoneId.of(properties.getTimeZone());
    }
}
