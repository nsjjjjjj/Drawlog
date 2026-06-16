package com.drawlog.feed;

import com.drawlog.config.AppProperties;
import com.drawlog.drawing.Drawing;
import com.drawlog.drawing.DrawingRepository;
import com.drawlog.drawing.DrawingService;
import com.drawlog.group.GroupMemberRepository;
import com.drawlog.group.GroupService;
import com.drawlog.topic.DailyTopic;
import com.drawlog.topic.TopicService;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final TopicService topicService;
    private final AppProperties properties;

    public FeedService(GroupService groupService, GroupMemberRepository memberRepository, DrawingRepository drawingRepository,
                       DrawingService drawingService, TopicService topicService, AppProperties properties) {
        this.groupService = groupService;
        this.memberRepository = memberRepository;
        this.drawingRepository = drawingRepository;
        this.drawingService = drawingService;
        this.topicService = topicService;
        this.properties = properties;
    }

    @Transactional
    public FeedDtos.FeedResponse feed(Long userId, Long groupId, LocalDate date) {
        groupService.requireGroup(userId, groupId);
        DailyTopic topic = topicService.ensureDailyTopic(userId, groupId, date);
        Map<Long, Drawing> drawingByUser = drawingRepository.findByGroupIdAndDailyTopicId(groupId, topic.getId()).stream()
                .collect(Collectors.toMap(drawing -> drawing.getUser().getId(), drawing -> drawing, (first, second) -> first));
        boolean submitted = drawingByUser.containsKey(userId);
        boolean locked = !date.isBefore(today()) && !submitted;
        return new FeedDtos.FeedResponse(
                date,
                topicService.toResponse(topic),
                submitted,
                locked,
                memberRepository.findByGroupIdOrderByJoinedAtAsc(groupId).stream()
                        .map(member -> new FeedDtos.MemberDrawingResponse(
                                member.getUser().getId(),
                                member.getUser().getNickname(),
                                member.getUser().getProfileImageUrl(),
                                member.getRole(),
                                member.getJoinedAt(),
                                locked ? null : drawingService.toResponse(drawingByUser.get(member.getUser().getId()))
                        ))
                        .toList()
        );
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(properties.getTimeZone()));
    }
}
