package com.drawlog.drawing;

import com.drawlog.group.FriendGroup;
import com.drawlog.topic.DailyTopic;
import com.drawlog.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "drawings")
public class Drawing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private FriendGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id")
    private DailyTopic topic;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public FriendGroup getGroup() { return group; }
    public void setGroup(FriendGroup group) { this.group = group; }
    public DailyTopic getTopic() { return topic; }
    public void setTopic(DailyTopic topic) { this.topic = topic; }
    public Instant getCreatedAt() { return createdAt; }
}
