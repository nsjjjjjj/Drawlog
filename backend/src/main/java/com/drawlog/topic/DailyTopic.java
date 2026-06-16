package com.drawlog.topic;

import com.drawlog.group.FriendGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "daily_topics", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "topic_date"}))
public class DailyTopic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private FriendGroup group;

    @Column(nullable = false, name = "topic_date")
    private LocalDate date;

    @Column(nullable = false, length = 120)
    private String text;

    @Column(nullable = false)
    private boolean fromSuggestion;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public FriendGroup getGroup() { return group; }
    public void setGroup(FriendGroup group) { this.group = group; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isFromSuggestion() { return fromSuggestion; }
    public void setFromSuggestion(boolean fromSuggestion) { this.fromSuggestion = fromSuggestion; }
    public Instant getCreatedAt() { return createdAt; }
}
