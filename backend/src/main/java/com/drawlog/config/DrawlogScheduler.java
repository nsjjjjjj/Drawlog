package com.drawlog.config;

import com.drawlog.drawing.DrawingService;
import com.drawlog.notification.NotificationService;
import com.drawlog.topic.TopicService;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DrawlogScheduler {
    private final AppProperties properties;
    private final TopicService topicService;
    private final DrawingService drawingService;
    private final NotificationService notificationService;

    public DrawlogScheduler(AppProperties properties, TopicService topicService, DrawingService drawingService,
                            NotificationService notificationService) {
        this.properties = properties;
        this.topicService = topicService;
        this.drawingService = drawingService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 50 23 * * *", zone = "${app.time-zone:Asia/Seoul}")
    public void closeTopicVotingWindow() {
        // The MVP has no mutable vote-closed row; selection at midnight reads the final persisted votes.
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.time-zone:Asia/Seoul}")
    public void selectTopicsAndLockDrawings() {
        LocalDate today = today();
        topicService.ensureTodayTopicsForAllGroups(today);
        drawingService.lockDrawingsBefore(today);
    }

    @Scheduled(cron = "0 0 12 * * *", zone = "${app.time-zone:Asia/Seoul}")
    public void remindUndrawnMembers() {
        LocalDate today = today();
        topicService.ensureTodayTopicsForAllGroups(today);
        notificationService.notifyUndrawnToday(today);
    }

    @Scheduled(cron = "0 0 23 * * *", zone = "${app.time-zone:Asia/Seoul}")
    public void remindUnvotedMembers() {
        notificationService.notifyUnvoted(today().plusDays(1));
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(properties.getTimeZone()));
    }
}
