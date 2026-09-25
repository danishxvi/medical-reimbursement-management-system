package com.mrms.escalation.internal;

import com.mrms.escalation.SlaAlert;
import com.mrms.escalation.internal.SlaEntities.Level;
import com.mrms.escalation.internal.SlaEntities.SlaEvent;
import com.mrms.identity.Accounts;
import com.mrms.organisation.OrganisationDirectory;
import com.mrms.shared.config.MrmsProperties;
import com.mrms.shared.domain.QueueSource;
import com.mrms.shared.domain.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches every queue stage against its time limit.
 *
 * <ul>
 *   <li>REMINDER at {@code reminderPercent} of the limit: the holder (or the
 *       queue's officials) is reminded.</li>
 *   <li>BREACH when the limit passes: officials and their supervisor are
 *       told, the employee is informed, and a record held by one official is
 *       put back in the queue if a colleague can take it.</li>
 *   <li>ESCALATED at {@code escalationFactor} times the limit: the Zonal
 *       Oversight officers of the school's zone (and administrators) are told.</li>
 * </ul>
 * Each step happens once per record and stage. The unique key on
 * {@code sla_event} makes the job safe to run on several servers at once:
 * the server whose insert wins sends the messages.
 */
@Component
class SlaWatch {

    private static final Logger log = LoggerFactory.getLogger(SlaWatch.class);

    private final List<QueueSource> sources;
    private final SlaEventRepository events;
    private final Accounts accounts;
    private final OrganisationDirectory organisation;
    private final ApplicationEventPublisher publisher;
    private final MrmsProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;

    SlaWatch(List<QueueSource> sources, SlaEventRepository events, Accounts accounts,
             OrganisationDirectory organisation, ApplicationEventPublisher publisher, MrmsProperties props,
             Clock clock, PlatformTransactionManager transactions) {
        this.sources = sources;
        this.events = events;
        this.accounts = accounts;
        this.organisation = organisation;
        this.publisher = publisher;
        this.props = props;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${mrms.sla.check-interval:PT15M}", initialDelayString = "PT1M")
    void scheduled() {
        if (!props.jobs().enabled()) {
            return;
        }
        try {
            runAt(clock.instant());
        } catch (RuntimeException e) {
            log.error("Time limit watch failed; it runs again at the next interval", e);
        }
    }

    /** One pass over every queue as of {@code now}. Returns how many steps were taken. */
    int runAt(Instant now) {
        int steps = 0;
        Set<String> open = new HashSet<>();
        for (QueueSource source : sources) {
            for (QueueSource.Item item : source.openItems()) {
                if (item.slaDays() <= 0 || item.stageEnteredAt() == null) {
                    continue;
                }
                open.add(item.subjectType() + "|" + item.subjectId() + "|" + item.stage() + "|" + item.stageEnteredAt());
                Duration limit = Duration.ofDays(item.slaDays());
                Duration elapsed = Duration.between(item.stageEnteredAt(), now);
                Duration reminderAt = limit.multipliedBy(props.sla().reminderPercent()).dividedBy(100);
                // A record first seen after its limit gets the breach message only, not a late reminder
                if (elapsed.compareTo(reminderAt) >= 0 && elapsed.compareTo(limit) < 0
                        && step(item, Level.REMINDER, source, now)) {
                    steps++;
                }
                if (elapsed.compareTo(limit) >= 0 && step(item, Level.BREACH, source, now)) {
                    steps++;
                }
                if (elapsed.compareTo(limit.multipliedBy(props.sla().escalationFactor())) >= 0
                        && step(item, Level.ESCALATED, source, now)) {
                    steps++;
                }
            }
        }
        resolveFinished(open, now);
        return steps;
    }

    /** Records the step and, if this server recorded it first, tells the people concerned. */
    private boolean step(QueueSource.Item item, Level level, QueueSource source, Instant now) {
        String zone = zoneOf(item.schoolId());
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                events.saveAndFlush(new SlaEvent(item.subjectType(), item.subjectId(),
                        item.reference() == null ? item.subjectType() + " " + item.subjectId() : item.reference(),
                        item.stage(), item.stageEnteredAt(), level, item.officeType(), item.officeId(), zone,
                        item.assignedTo(), item.slaDays(), now));
                boolean released = false;
                List<Long> officials = accounts.activeUserIds(item.officialRole(), item.officeId());
                Set<Long> recipients = new LinkedHashSet<>();
                switch (level) {
                    case REMINDER -> {
                        if (item.assignedTo() != null) {
                            recipients.add(item.assignedTo());
                        } else {
                            recipients.addAll(officials);
                        }
                    }
                    case BREACH -> {
                        recipients.addAll(officials);
                        if (item.supervisorRole() != null) {
                            recipients.addAll(accounts.activeUserIds(item.supervisorRole(), item.officeId()));
                        }
                        // Held by one person while others could do it: back to the queue
                        if (item.assignedTo() != null && officials.size() > 1) {
                            released = source.releaseToQueue(item.subjectId(), item.stage(), item.stageEnteredAt());
                        }
                    }
                    case ESCALATED -> {
                        recipients.addAll(accounts.activeOversightIds(zone));
                        recipients.addAll(accounts.activeUserIds(Role.ADMIN, null));
                    }
                }
                publisher.publishEvent(new SlaAlert(level.name(), item.subjectType(), item.subjectId(),
                        item.reference(), item.stageLabel(), item.slaDays(), new ArrayList<>(recipients),
                        level == Level.BREACH ? item.employeeUserId() : null, released));
                return true;
            }));
        } catch (DataIntegrityViolationException alreadyRecorded) {
            return false;
        }
    }

    /** Steps of records that have moved on are closed; the time they closed shows how long the delay lasted. */
    private void resolveFinished(Set<String> open, Instant now) {
        tx.executeWithoutResult(status -> events.findByResolvedAtIsNull().stream()
                .filter(e -> !open.contains(e.key()))
                .forEach(e -> e.resolve(now)));
    }

    String zoneOf(Long schoolId) {
        return schoolId == null ? null : organisation.school(schoolId).map(s -> s.zone()).orElse(null);
    }
}
