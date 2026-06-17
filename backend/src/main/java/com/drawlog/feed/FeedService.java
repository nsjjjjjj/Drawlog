package com.drawlog.feed;

import com.drawlog.common.ApiException;
import com.drawlog.common.ErrorCode;
import com.drawlog.config.AppProperties;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.drawing.DrawingService;
import com.drawlog.group.GroupMemberRepository;
import com.drawlog.group.GroupService;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.DailyTopicRepository;
import com.drawlog.topic.TopicService;
import com.drawlog.user.UserStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedService {
    private final GroupService groupService;
    private final GroupMemberRepository memberRepository;
    private final DrawingRepository drawingRepository;
    private final DrawingService drawingService;
    private final DailyTopicRepository dailyTopicRepository;
    private final TopicService topicService;
    private final AppProperties properties;

    public FeedService(GroupService groupService, GroupMemberRepository memberRepository, DrawingRepository drawingRepository,
                       DrawingService drawingService, DailyTopicRepository dailyTopicRepository, TopicService topicService, AppProperties properties) {
        this.groupService = groupService;
        this.memberRepository = memberRepository;
        this.drawingRepository = drawingRepository;
        this.drawingService = drawingService;
        this.dailyTopicRepository = dailyTopicRepository;
        this.topicService = topicService;
        this.properties = properties;
    }

    @Transactional
    public FeedDtos.FeedResponse feed(Long userId, Long groupId, LocalDate date) {
        groupService.requireGroup(userId, groupId);
        LocalDate today = today();
        if (date.isAfter(today)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "미래 날짜는 조회할 수 없습니다.");
        }
        DailyTopic topic = dailyTopicRepository.findByGroupIdAndTopicDate(groupId, date).orElse(null);
        List<Drawing> drawings = topic == null ? List.of() : drawingRepository.findByGroupIdAndDailyTopicId(groupId, topic.getId());
        boolean hasRecords = !drawings.isEmpty();
        Map<Long, Drawing> drawingByUser = drawings.stream()
                .collect(Collectors.toMap(drawing -> drawing.getUser().getId(), drawing -> drawing, (first, second) -> first));
        boolean submitted = drawingByUser.containsKey(userId);
        boolean locked = date.equals(today) && topic != null && !submitted;
        return new FeedDtos.FeedResponse(
                date,
                (hasRecords || (date.equals(today) && topic != null)) ? topicService.toResponse(topic) : null,
                submitted,
                locked,
                memberRepository.findByGroupIdAndUserStatusOrderByJoinedAtAsc(groupId, UserStatus.ACTIVE).stream()
                        .map(member -> new FeedDtos.MemberDrawingResponse(
                                member.getUser().getId(),
                                member.getUser().getNickname(),
                                member.getUser().getProfileImageUrl(),
                                member.getUser().getStatus(),
                                member.getRole(),
                                member.getJoinedAt(),
                                locked ? null : drawingService.toResponse(drawingByUser.get(member.getUser().getId()))
                        ))
                        .toList()
        );
    }

    @Transactional
    public FeedDtos.FeedDatesResponse dates(Long userId, Long groupId) {
        groupService.requireGroup(userId, groupId);
        return new FeedDtos.FeedDatesResponse(drawingRepository.findRecordDates(groupId, today()));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(properties.getTimeZone()));
    }
}
