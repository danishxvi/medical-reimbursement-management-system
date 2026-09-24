package com.mrms.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notification")
class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String message;

    private String link;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    Notification(Long recipientUserId, String title, String message, String link, Instant createdAt) {
        this.recipientUserId = recipientUserId;
        this.title = title;
        this.message = message.length() > 500 ? message.substring(0, 497) + "..." : message;
        this.link = link;
        this.createdAt = createdAt;
    }

    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    Long getId() { return id; }
    Long getRecipientUserId() { return recipientUserId; }
    String getTitle() { return title; }
    String getMessage() { return message; }
    String getLink() { return link; }
    Instant getReadAt() { return readAt; }
    Instant getCreatedAt() { return createdAt; }
}
