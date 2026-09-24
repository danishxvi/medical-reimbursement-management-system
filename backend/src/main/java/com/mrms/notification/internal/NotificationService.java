package com.mrms.notification.internal;

import com.mrms.claim.ClaimStatusChanged;
import com.mrms.enac.NacStatusChanged;
import com.mrms.identity.Accounts;
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
    private final Accounts accounts;
    private final Clock clock;

    NotificationService(NotificationRepository repository, Accounts accounts, Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
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
            case RETURNED_BY_HOS, RETURNED_BY_PAO -> send(e.employeeUserId(), ref + " returned for correction",
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
            case SANCTIONED -> send(e.employeeUserId(), ref + " sanctioned",
                    "Your claim is sanctioned. It will be paid, oldest first, as soon as funds are available.", link);
            case PAID -> send(e.employeeUserId(), ref + " paid",
                    "The admitted amount will be credited with your salary." + remarks, link);
            case REJECTED -> send(e.employeeUserId(), ref + " rejected",
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
            case ISSUED -> send(e.employeeUserId(), "e-NAC issued",
                    "Certificate " + e.nacNumber() + " is issued. Items marked not available can now be claimed.", link);
            case RETURNED -> send(e.employeeUserId(), "Prescription returned by dispensary",
                    "Please correct and resubmit." + remarks, link);
        }
    }

    private void sendToOffice(Role role, Long officeId, String title, String message, String link) {
        accounts.activeUserIds(role, officeId).forEach(id -> send(id, title, message, link));
    }

    private void send(Long userId, String title, String message, String link) {
        repository.save(new Notification(userId, title, message, link, clock.instant()));
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
