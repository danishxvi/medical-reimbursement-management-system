package com.mrms.notification.internal;

import com.mrms.claim.ClaimStatusChanged;
import com.mrms.enac.NacStatusChanged;
import com.mrms.escalation.SlaAlert;
import com.mrms.identity.Accounts;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.Role;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Limit limit);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}

/**
 * Turns workflow events into messages. Listeners run synchronously in the
 * publishing transaction, so a notification exists if and only if the
 * status change was committed.
 */
@Service
class NotificationService {

    private final NotificationRepository repository;
    private final OutboundMessageRepository outbox;
    private final MessageChannels channels;
    private final Accounts accounts;
    private final MrmsProperties props;
    private final Clock clock;

    NotificationService(NotificationRepository repository, OutboundMessageRepository outbox, MessageChannels channels,
                        Accounts accounts, MrmsProperties props, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.channels = channels;
        this.accounts = accounts;
        this.props = props;
        this.clock = clock;
    }

    @EventListener
    void on(ClaimStatusChanged e) {
        String ref = e.claimNumber() == null ? "Your claim" : "Claim " + e.claimNumber();
        String link = "/claims/" + e.claimId();
        String remarks = e.remarks() == null ? "" : " Remarks: " + e.remarks();
        switch (e.to()) {
            case PENDING_HOS -> {
                send(e.employeeUserId(), ref + " submitted", "It is now in the Head of School's queue.", link);
                sendToOffice(Role.HOS, e.schoolId(), "New claim to verify",
                        ref + " is waiting in your queue.", "/queue");
            }
            case RETURNED_BY_HOS, RETURNED_BY_PAO -> alert(e.employeeUserId(), ref + " returned for correction",
                    "Please correct and resubmit. It keeps its place in the queue." + remarks, link);
            case PENDING_PAO_AUDIT -> {
                if (e.from() == com.mrms.claim.ClaimStatus.PENDING_HOS) {
                    send(e.employeeUserId(), ref + " forwarded to PAO",
                            "The Head of School has verified and forwarded your claim.", link);
                }
                if (e.assignedTo() != null) {
                    send(e.assignedTo(), ref + " sent back to you", "The PAO officer sent it back." + remarks,
                            "/queue");
                } else {
                    sendToOffice(Role.PAO_AUDITOR, e.paoId(), "New claim to scrutinise",
                            ref + " is waiting in your queue.", "/queue");
                }
            }
            case PENDING_SANCTION -> sendToOffice(Role.PAO_OFFICER, e.paoId(), "Claim awaiting sanction",
                    ref + " has been scrutinised and is waiting for sanction.", "/queue");
            case SANCTIONED -> alert(e.employeeUserId(), ref + " sanctioned",
                    "Your claim is sanctioned. It will be paid, oldest first, as soon as funds are available.", link);
            case PAID -> alert(e.employeeUserId(), ref + " paid",
                    "The admitted amount will be credited with your salary." + remarks, link);
            case REJECTED -> alert(e.employeeUserId(), ref + " rejected",
                    "Reason: " + (e.remarks() == null ? "not recorded" : e.remarks()), link);
            default -> {
                // Drafts and withdrawals need no message
            }
        }
    }

    @EventListener
    void on(NacStatusChanged e) {
        String link = "/nac/" + e.nacRequestId();
        String remarks = e.remarks() == null ? "" : " Remarks: " + e.remarks();
        switch (e.newStatus()) {
            case PENDING_PHARMACIST -> {
                if (e.assignedPharmacistId() != null) {
                    send(e.assignedPharmacistId(), "Certificate sent back to you",
                            "The Medical Officer asked you to review your decisions." + remarks, "/queue");
                } else {
                    sendToOffice(Role.PHARMACIST, e.dispensaryId(), "New prescription to verify",
                            "A prescription is waiting in your queue.", "/queue");
                }
            }
            case PENDING_MEDICAL_OFFICER -> sendToOffice(Role.MEDICAL_OFFICER, e.dispensaryId(),
                    "Certificate awaiting countersignature", "A verified prescription is waiting in your queue.",
                    "/queue");
            case ISSUED -> alert(e.employeeUserId(), "e-NAC issued",
                    "Certificate " + e.nacNumber() + " is issued. Items marked not available can now be claimed.", link);
            case RETURNED -> alert(e.employeeUserId(), "Prescription returned by dispensary",
                    "Please correct and resubmit." + remarks, link);
        }
    }

    /** Time limit steps and reminders from the zonal office. */
    @EventListener
    void on(SlaAlert e) {
        String ref = e.reference() == null ? "A record" : e.reference();
        String stage = e.stageLabel() == null ? "its current stage" : e.stageLabel().toLowerCase(java.util.Locale.ROOT);
        String employeeLink = ("CLAIM".equals(e.subjectType()) ? "/claims/" : "/nac/") + e.subjectId();
        switch (e.level()) {
            case "REMINDER" -> e.recipients().forEach(id -> alert(id, "Time limit approaching: " + ref,
                    ref + " is " + stage + " and should be completed within " + e.slaDays()
                            + " days. It is near its limit.", "/queue"));
            case "BREACH" -> {
                e.recipients().forEach(id -> alert(id, "Time limit passed: " + ref,
                        ref + " has passed its " + e.slaDays() + " day limit (" + stage + "). The delay is recorded."
                                + (e.released() ? " It was put back in the queue so a colleague can take it." : ""),
                        "/queue"));
                if (e.employeeUserId() != null) {
                    alert(e.employeeUserId(), ref + " is taking longer than it should",
                            "It has passed the " + e.slaDays() + " day limit (" + stage
                                    + "). The office has been reminded and the delay is recorded.", employeeLink);
                }
            }
            case "ESCALATED" -> e.recipients().forEach(id -> alert(id, "Escalated: " + ref,
                    ref + " has waited more than twice its " + e.slaDays() + " day limit (" + stage + ").",
                    "/oversight"));
            case "NUDGE" -> e.recipients().forEach(id -> alert(id, "Reminder from the zonal office: " + ref,
                    ref + " is past its time limit (" + stage + "). Please complete it.", "/queue"));
            default -> {
                // unknown level: nothing to send
            }
        }
    }

    private void sendToOffice(Role role, Long officeId, String title, String message, String link) {
        accounts.activeUserIds(role, officeId).forEach(id -> send(id, title, message, link));
    }

    private void send(Long userId, String title, String message, String link) {
        repository.save(new Notification(userId, title, message, link, clock.instant()));
    }

    /**
     * In the portal, and by e-mail and SMS where the user has them on record
     * and the channel is enabled. E-mail and SMS carry the title only (a
     * claim number and what happened), never remarks or health details: the
     * user signs in to read more.
     */
    private void alert(Long userId, String title, String message, String link) {
        send(userId, title, message, link);
        if (!channels.emailEnabled() && !channels.smsEnabled()) {
            return;
        }
        accounts.find(userId).ifPresent(a -> {
            Instant now = clock.instant();
            String portal = props.notifications().portalUrl();
            if (channels.emailEnabled() && a.email() != null && !a.email().isBlank()) {
                String body = "Dear " + a.fullName() + ",\n\n" + title + ".\n\nSign in to MRMS to see the details"
                        + (portal.isBlank() ? "." : ": " + portal + link) + "\n\n"
                        + "This is an automatic message from the Medical Reimbursement Management System. "
                        + "Please do not reply.";
                outbox.save(new OutboundMessage(OutboundMessage.Channel.EMAIL, userId, a.email(), "MRMS: " + title,
                        body, now));
            }
            if (channels.smsEnabled() && a.mobile() != null && !a.mobile().isBlank()) {
                String sms = "MRMS: " + title + ". Sign in to MRMS for details.";
                outbox.save(new OutboundMessage(OutboundMessage.Channel.SMS, userId, a.mobile(), title,
                        sms.length() > 300 ? sms.substring(0, 300) : sms, now));
            }
        });
    }

    // ------------------------------------------------------------------
    // Reading (own notifications only)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    List<Notification> latest(Long userId) {
        return repository.findByRecipientUserIdOrderByCreatedAtDesc(userId, Limit.of(50));
    }

    @Transactional(readOnly = true)
    long unread(Long userId) {
        return repository.countByRecipientUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    void markRead(Long userId, Long id) {
        repository.findById(id).filter(n -> n.getRecipientUserId().equals(userId))
                .ifPresent(n -> n.markRead(clock.instant()));
    }

    @Transactional
    void markAllRead(Long userId) {
        repository.markAllRead(userId, clock.instant());
    }
}
