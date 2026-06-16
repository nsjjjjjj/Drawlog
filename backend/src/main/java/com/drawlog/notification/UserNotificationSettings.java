package com.drawlog.notification;

import com.drawlog.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_notification_settings")
public class UserNotificationSettings {
    @Id
    private Long userId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private boolean allEnabled = true;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getUserId() { return userId; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public boolean isAllEnabled() { return allEnabled; }
    public void setAllEnabled(boolean allEnabled) { this.allEnabled = allEnabled; this.updatedAt = Instant.now(); }
    public Instant getUpdatedAt() { return updatedAt; }
}
