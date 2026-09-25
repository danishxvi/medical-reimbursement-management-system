package com.mrms.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * An e-mail or SMS waiting to be sent. Messages are written in the same
 * transaction as the event they report and sent afterwards by a background
 * job, so a message is never lost and never sent for something that was
 * rolled back.
 */
@Entity
@Table(name = "outbound_message")
class OutboundMessage {

    enum Channel { EMAIL, SMS }

    enum Status { PENDING, SENDING, SENT, FAILED }

    /** Waits between attempts; after the last one the message is marked FAILED. */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboundMessage() {
    }

    OutboundMessage(Channel channel, Long recipientUserId, String destination, String subject, String body,
                    Instant now) {
        this.channel = channel;
        this.recipientUserId = recipientUserId;
        this.destination = destination;
        this.subject = subject.length() > 200 ? subject.substring(0, 200) : subject;
        this.body = body.length() > 2000 ? body.substring(0, 2000) : body;
        this.status = Status.PENDING;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    void sent(Instant now) {
        this.status = Status.SENT;
        this.sentAt = now;
        this.attempts++;
        this.lastError = null;
    }

    void failed(String error, Instant now) {
        this.attempts++;
        this.lastError = error == null ? "Unknown error" : error.length() > 300 ? error.substring(0, 300) : error;
        if (attempts >= BACKOFF.size()) {
            this.status = Status.FAILED;
        } else {
            this.status = Status.PENDING;
            this.nextAttemptAt = now.plus(BACKOFF.get(attempts - 1));
        }
    }

    Long getId() { return id; }
    Channel getChannel() { return channel; }
    String getDestination() { return destination; }
    String getSubject() { return subject; }
    String getBody() { return body; }
    Status getStatus() { return status; }
}

interface OutboundMessageRepository extends JpaRepository<OutboundMessage, Long> {

    @Query("select m.id from OutboundMessage m where m.status = :pending and m.nextAttemptAt <= :now order by m.id")
    List<Long> due(@Param("pending") OutboundMessage.Status pending, @Param("now") Instant now, Limit limit);

    /**
     * Claims a message for this server. Only one server's update succeeds,
     * so a message is sent once even when several servers run the job.
     */
    @Modifying
    @Query("update OutboundMessage m set m.status = :sending where m.id = :id and m.status = :pending")
    int claim(@Param("id") Long id, @Param("pending") OutboundMessage.Status pending,
              @Param("sending") OutboundMessage.Status sending);

    List<OutboundMessage> findByRecipientUserIdOrderByIdDesc(Long recipientUserId);
}
